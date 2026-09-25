package io.github.tsndre.minijava.store.repository;

import io.github.tsndre.minijava.store.model.Category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository {

    Category create(Category category);

    List<Category> findAll();

    Optional<Category> findById(UUID id);

    Category update(Category category);

    void delete(UUID id);
}
