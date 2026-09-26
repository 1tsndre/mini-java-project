package io.github.tsndre.minijava.store.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Set;

/**
 * Logs every statement the application runs, on a single line with its duration and row count,
 * like the Go service's query tracer in development. Logging starts once the application is ready,
 * so the startup migrations stay out of the log.
 */
@Slf4j
public final class SqlLoggingDataSource extends DelegatingDataSource {

    private static final Set<String> EXECUTE_METHODS =
            Set.of("execute", "executeQuery", "executeUpdate", "executeLargeUpdate");

    private volatile boolean enabled;

    private SqlLoggingDataSource(DataSource target) {
        super(target);
    }

    static SqlLoggingDataSource wrap(DataSource target) {
        return new SqlLoggingDataSource(target);
    }

    public void enable() {
        enabled = true;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return proxy(Connection.class, super.getConnection(), this::onConnection);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return proxy(Connection.class, super.getConnection(username, password), this::onConnection);
    }

    private Object onConnection(Object target, Method method, Object[] args) throws Throwable {
        Object result = invoke(target, method, args);
        if (!enabled) {
            return result;
        }
        if (result instanceof PreparedStatement statement && method.getName().equals("prepareStatement")) {
            String sql = (String) args[0];
            return proxy(PreparedStatement.class, statement, (t, m, a) -> onStatement(t, m, a, sql));
        }
        if (result instanceof Statement statement && method.getName().equals("createStatement")) {
            return proxy(Statement.class, statement, (t, m, a) -> onStatement(t, m, a, null));
        }
        return result;
    }

    private Object onStatement(Object target, Method method, Object[] args, String preparedSql) throws Throwable {
        if (!EXECUTE_METHODS.contains(method.getName())) {
            return invoke(target, method, args);
        }
        String sql = preparedSql != null ? preparedSql : args != null && args.length > 0 ? (String) args[0] : "";
        long start = System.nanoTime();
        Object result;
        try {
            result = invoke(target, method, args);
        } catch (SQLException e) {
            log.atError().setCause(e)
                    .addKeyValue("sql", singleLine(sql))
                    .addKeyValue("duration", since(start))
                    .addKeyValue("rows", 0)
                    .log("query failed");
            throw e;
        }
        if (result instanceof ResultSet resultSet) {
            // Like pgx, a query is finished once its rows are closed.
            long[] rows = {0};
            return proxy(ResultSet.class, resultSet, (t, m, a) -> {
                Object value = invoke(t, m, a);
                if (m.getName().equals("next") && Boolean.TRUE.equals(value)) {
                    rows[0]++;
                } else if (m.getName().equals("close")) {
                    logQuery(sql, start, rows[0]);
                }
                return value;
            });
        }
        if (result instanceof Number count) {
            logQuery(sql, start, count.longValue());
        } else {
            Statement statement = (Statement) target;
            logQuery(sql, start, Math.max(statement.getUpdateCount(), 0));
        }
        return result;
    }

    private static void logQuery(String sql, long start, long rows) {
        log.atDebug()
                .addKeyValue("sql", singleLine(sql))
                .addKeyValue("duration", since(start))
                .addKeyValue("rows", rows)
                .log("query");
    }

    /** The queries are written across several lines; log each one on a single line. */
    static String singleLine(String sql) {
        return String.join(" ", sql.strip().split("\\s+"));
    }

    private static String since(long start) {
        return GoDuration.format(Duration.ofNanos(System.nanoTime() - start));
    }

    private interface Handler {
        Object handle(Object target, Method method, Object[] args) throws Throwable;
    }

    private static <T> T proxy(Class<T> type, T target, Handler handler) {
        InvocationHandler invocationHandler = (proxy, method, args) -> handler.handle(target, method, args);
        return type.cast(Proxy.newProxyInstance(SqlLoggingDataSource.class.getClassLoader(),
                new Class<?>[] {type}, invocationHandler));
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
