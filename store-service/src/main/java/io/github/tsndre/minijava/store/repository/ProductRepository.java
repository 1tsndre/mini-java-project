package io.github.tsndre.minijava.store.repository;

import io.github.tsndre.minijava.store.model.Product;
import io.github.tsndre.minijava.store.model.ProductFilter;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {

    Product create(Product product);

    PageResult<Product> findAll(ProductFilter filter);

    Optional<Product> findById(UUID id);

    /**
     * Saves everything but the stock and returns the stored row. Stock changes go through
     * {@link #updateStock} or the atomic checkout/cancel paths, otherwise a stale read here would
     * overwrite a concurrent checkout's decrement.
     */
    Product update(Product product);

    void delete(UUID id);

    void updateStock(UUID id, int quantity);
}
