package io.github.tsndre.minijava.store.repository;

import io.github.tsndre.minijava.store.constant.Role;
import io.github.tsndre.minijava.store.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcUserRepository implements UserRepository {

    static final String COLUMNS = "id, email, password, name, role, created_at, updated_at";

    private final JdbcClient jdbc;

    @Override
    public User create(User user) {
        try {
            return jdbc.sql("""
                            INSERT INTO users (email, password, name, role)
                            VALUES (?, ?, ?, ?)
                            RETURNING %s""".formatted(COLUMNS))
                    .params(user.getEmail(), user.getPassword(), user.getName(), user.getRole().value())
                    .query(RowMappers.USER)
                    .single();
        } catch (DataAccessException e) {
            throw DbErrors.translate(e);
        }
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM users WHERE id = ?")
                .param(id)
                .query(RowMappers.USER)
                .optional();
    }

    /**
     * Matches case-insensitively, which also finds accounts stored with mixed-case emails
     * before registration started lower-casing them. If such an old account has a case
     * variant, the older one wins.
     */
    @Override
    public Optional<User> findByEmail(String email) {
        return jdbc.sql("""
                        SELECT %s
                        FROM users
                        WHERE lower(email) = lower(?)
                        ORDER BY created_at, id
                        LIMIT 1""".formatted(COLUMNS))
                .param(email)
                .query(RowMappers.USER)
                .optional();
    }

    @Override
    public void updateRole(UUID id, Role role) {
        try {
            jdbc.sql("UPDATE users SET role = ?, updated_at = NOW() WHERE id = ?")
                    .params(role.value(), id)
                    .update();
        } catch (DataAccessException e) {
            throw DbErrors.translate(e);
        }
    }
}
