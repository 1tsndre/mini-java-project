package io.github.tsndre.minijava.store.repository;

import io.github.tsndre.minijava.store.model.Store;

import java.util.Optional;
import java.util.UUID;

public interface StoreRepository {

    Store create(Store store);

    Optional<Store> findById(UUID id);

    Optional<Store> findByUserId(UUID userId);

    /** Saves name, description and logo, and returns the stored row. */
    Store update(Store store);

    void delete(UUID id);
}
