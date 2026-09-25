package io.github.tsndre.minijava.store.repository;

import io.github.tsndre.minijava.store.constant.Role;
import io.github.tsndre.minijava.store.model.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    /** Inserts the user and returns the stored row, with its generated id and timestamps. */
    User create(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String email);

    void updateRole(UUID id, Role role);
}
