package io.github.tsndre.minijava.store.repository;

import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;

import java.sql.SQLException;

/**
 * Maps driver errors to the repository exceptions, so services can tell missing rows and
 * constraint violations apart without depending on the database driver.
 */
final class DbErrors {

    /** PostgreSQL error codes: https://www.postgresql.org/docs/current/errcodes-appendix.html */
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String FOREIGN_KEY_VIOLATION = "23503";

    private DbErrors() {
    }

    static RuntimeException translate(DataAccessException e) {
        if (e instanceof EmptyResultDataAccessException) {
            return new RecordNotFoundException();
        }
        if (NestedExceptionUtils.getMostSpecificCause(e) instanceof SQLException sql) {
            if (UNIQUE_VIOLATION.equals(sql.getSQLState())) {
                return new UniqueViolationException(sql);
            }
            if (FOREIGN_KEY_VIOLATION.equals(sql.getSQLState())) {
                return new ForeignKeyViolationException(sql);
            }
        }
        return e;
    }
}
