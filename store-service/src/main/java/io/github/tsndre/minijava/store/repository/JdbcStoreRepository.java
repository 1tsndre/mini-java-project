package io.github.tsndre.minijava.store.repository;

import io.github.tsndre.minijava.store.model.Store;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcStoreRepository implements StoreRepository {

    static final String COLUMNS = "id, user_id, name, description, logo_url, created_at, updated_at";

    private final JdbcClient jdbc;

    @Override
    public Store create(Store store) {
        try {
            return jdbc.sql("""
                            INSERT INTO stores (user_id, name, description, logo_url)
                            VALUES (?, ?, ?, ?)
                            RETURNING %s""".formatted(COLUMNS))
                    .params(store.getUserId(), store.getName(), store.getDescription(), store.getLogoUrl())
                    .query(RowMappers.STORE)
                    .single();
        } catch (DataAccessException e) {
            throw DbErrors.translate(e);
        }
    }

    @Override
    public Optional<Store> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM stores WHERE id = ?")
                .param(id)
                .query(RowMappers.STORE)
                .optional();
    }

    @Override
    public Optional<Store> findByUserId(UUID userId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM stores WHERE user_id = ?")
                .param(userId)
                .query(RowMappers.STORE)
                .optional();
    }

    @Override
    public Store update(Store store) {
        try {
            return jdbc.sql("""
                            UPDATE stores
                            SET name = ?, description = ?, logo_url = ?, updated_at = NOW()
                            WHERE id = ?
                            RETURNING %s""".formatted(COLUMNS))
                    .params(store.getName(), store.getDescription(), store.getLogoUrl(), store.getId())
                    .query(RowMappers.STORE)
                    .single();
        } catch (DataAccessException e) {
            throw DbErrors.translate(e);
        }
    }

    @Override
    public void delete(UUID id) {
        try {
            jdbc.sql("DELETE FROM stores WHERE id = ?").param(id).update();
        } catch (DataAccessException e) {
            throw DbErrors.translate(e);
        }
    }
}
