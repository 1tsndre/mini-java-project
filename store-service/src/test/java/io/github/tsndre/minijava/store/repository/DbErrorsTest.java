package io.github.tsndre.minijava.store.repository;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class DbErrorsTest {

    private static DataAccessException withState(String sqlState) {
        return new DataIntegrityViolationException("violation", new SQLException("violation", sqlState));
    }

    @Test
    void noRowsIsRecordNotFound() {
        assertThat(DbErrors.translate(new EmptyResultDataAccessException(1))).isInstanceOf(RecordNotFoundException.class);
    }

    @Test
    void uniqueViolation() {
        assertThat(DbErrors.translate(withState("23505"))).isInstanceOf(UniqueViolationException.class);
    }

    @Test
    void foreignKeyViolation() {
        assertThat(DbErrors.translate(withState("23503"))).isInstanceOf(ForeignKeyViolationException.class);
    }

    @Test
    void otherConstraintViolationIsPassedThrough() {
        DataAccessException checkViolation = withState("23514");
        assertThat(DbErrors.translate(checkViolation)).isSameAs(checkViolation);
    }

    @Test
    void otherErrorIsPassedThrough() {
        DataAccessException plain = new DataAccessResourceFailureException("connection reset");
        assertThat(DbErrors.translate(plain)).isSameAs(plain);
    }

    @Test
    void constraintViolationsKeepTheDriverErrorForLogging() {
        SQLException driverError = new SQLException("users_email_key", "23505");
        RuntimeException translated = DbErrors.translate(new DataIntegrityViolationException("dup", driverError));
        assertThat(translated).hasCause(driverError);
    }
}
