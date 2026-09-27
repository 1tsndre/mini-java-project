package io.github.tsndre.minijava.store.service;

import io.github.tsndre.minijava.store.repository.ForeignKeyViolationException;
import io.github.tsndre.minijava.store.repository.UniqueViolationException;
import org.springframework.dao.DataAccessResourceFailureException;

import java.sql.SQLException;

final class TestErrors {

    private TestErrors() {
    }

    static RuntimeException dbError() {
        return new DataAccessResourceFailureException("db error");
    }

    static UniqueViolationException duplicateKey() {
        return new UniqueViolationException(new SQLException("duplicate key value violates unique constraint", "23505"));
    }

    static ForeignKeyViolationException foreignKeyViolation() {
        return new ForeignKeyViolationException(new SQLException("violates foreign key constraint", "23503"));
    }
}
