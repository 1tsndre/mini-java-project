package io.github.tsndre.minijava.store.repository;

import io.github.tsndre.minijava.store.config.SqlLoggingDataSource;
import io.github.tsndre.minijava.store.model.OutboxMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Repository
@RequiredArgsConstructor
public class JdbcOutboxRepository implements OutboxRepository {

    static final String COLUMNS = "id, topic, payload, created_at";

    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;

    @Override
    public PublishResult publishPending(int limit, Consumer<OutboxMessage> publish) {
        // The relay polls every second; logging each poll would bury the other queries.
        return SqlLoggingDataSource.withoutQueryLog(() -> publish(limit, publish));
    }

    private PublishResult publish(int limit, Consumer<OutboxMessage> publish) {
        try {
            return transactions.execute(status -> {
                // The row locks keep a concurrent relay from publishing the same messages.
                List<OutboxMessage> messages = jdbc.sql(
                                "SELECT " + COLUMNS + " FROM outbox ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED")
                        .param(limit)
                        .query(RowMappers.OUTBOX_MESSAGE)
                        .list();

                int published = 0;
                for (OutboxMessage message : messages) {
                    try {
                        publish.accept(message);
                    } catch (RuntimeException e) {
                        // Keep the rest queued, in order, for the next run.
                        return new PublishResult(published, Optional.of(e));
                    }
                    jdbc.sql("DELETE FROM outbox WHERE id = ?").param(message.id()).update();
                    published++;
                }
                return new PublishResult(published, Optional.empty());
            });
        } catch (DataAccessException e) {
            throw DbErrors.translate(e);
        }
    }

    /** Queues a message in the transaction of the change it announces. */
    static void insert(JdbcClient jdbc, String topic, String payload) {
        jdbc.sql("INSERT INTO outbox (topic, payload) VALUES (?, ?)").params(topic, payload).update();
    }
}
