package io.github.tsndre.minijava.store.repository;

import io.github.tsndre.minijava.store.model.Category;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcCategoryRepository implements CategoryRepository {

    static final String COLUMNS = "id, name, created_at, updated_at";

    private final JdbcClient jdbc;

    @Override
    public Category create(Category category) {
        try {
            return jdbc.sql("INSERT INTO categories (name) VALUES (?) RETURNING " + COLUMNS)
                    .param(category.getName())
                    .query(RowMappers.CATEGORY)
                    .single();
        } catch (DataAccessException e) {
            throw DbErrors.translate(e);
        }
    }

    @Override
    public List<Category> findAll() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM categories ORDER BY name ASC")
                .query(RowMappers.CATEGORY)
                .list();
    }

    @Override
    public Optional<Category> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM categories WHERE id = ?")
                .param(id)
                .query(RowMappers.CATEGORY)
                .optional();
    }

    @Override
    public Category update(Category category) {
        try {
            return jdbc.sql("UPDATE categories SET name = ?, updated_at = NOW() WHERE id = ? RETURNING " + COLUMNS)
                    .params(category.getName(), category.getId())
                    .query(RowMappers.CATEGORY)
                    .single();
        } catch (DataAccessException e) {
            throw DbErrors.translate(e);
        }
    }

    @Override
    public void delete(UUID id) {
        try {
            jdbc.sql("DELETE FROM categories WHERE id = ?").param(id).update();
        } catch (DataAccessException e) {
            throw DbErrors.translate(e);
        }
    }
}
