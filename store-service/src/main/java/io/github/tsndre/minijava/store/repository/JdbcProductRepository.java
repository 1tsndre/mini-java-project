package io.github.tsndre.minijava.store.repository;

import io.github.tsndre.minijava.store.constant.CacheKey;
import io.github.tsndre.minijava.store.model.Product;
import io.github.tsndre.minijava.store.model.ProductFilter;
import io.github.tsndre.minijava.store.repository.cache.Cache;
import io.github.tsndre.minijava.store.util.GoDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcProductRepository implements ProductRepository {

    static final String COLUMNS =
            "id, store_id, category_id, name, description, price, stock, image_url, created_at, updated_at";

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("price", "name", "created_at");

    private final JdbcClient jdbc;
    private final Cache cache;

    record Where(String sql, List<Object> args) {
    }

    @Override
    public Product create(Product product) {
        try {
            return jdbc.sql("""
                            INSERT INTO products (store_id, category_id, name, description, price, stock, image_url)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            RETURNING %s""".formatted(COLUMNS))
                    .params(product.getStoreId(), product.getCategoryId(), product.getName(),
                            product.getDescription(), product.getPrice(), product.getStock(), product.getImageUrl())
                    .query(RowMappers.PRODUCT)
                    .single();
        } catch (DataAccessException e) {
            throw DbErrors.translate(e);
        }
    }

    @Override
    public PageResult<Product> findAll(ProductFilter filter) {
        Where where = filterQuery(filter);

        long total = jdbc.sql("SELECT COUNT(*) FROM products" + where.sql())
                .params(where.args())
                .query(Long.class)
                .single();

        List<Object> args = new ArrayList<>(where.args());
        args.add(filter.perPage());
        args.add((filter.page() - 1) * filter.perPage());
        List<Product> products = jdbc.sql("SELECT " + COLUMNS + " FROM products" + where.sql() + orderBy(filter)
                        + " LIMIT ? OFFSET ?")
                .params(args)
                .query(RowMappers.PRODUCT)
                .list();
        return new PageResult<>(products, total);
    }

    /** Builds the WHERE clause for the filter, with the matching arguments in placeholder order. */
    static Where filterQuery(ProductFilter filter) {
        List<String> conditions = new ArrayList<>();
        List<Object> args = new ArrayList<>();

        if (!filter.categoryId().isEmpty()) {
            conditions.add("category_id = CAST(? AS uuid)");
            args.add(filter.categoryId());
        }
        if (!filter.storeId().isEmpty()) {
            conditions.add("store_id = CAST(? AS uuid)");
            args.add(filter.storeId());
        }
        if (!filter.search().isEmpty()) {
            String search = "%" + filter.search() + "%";
            conditions.add("(name ILIKE ? OR description ILIKE ?)");
            args.add(search);
            args.add(search);
        }
        if (!filter.minPrice().isEmpty()) {
            GoDecimal.parse(filter.minPrice()).ifPresent(minPrice -> {
                conditions.add("price >= ?");
                args.add(minPrice);
            });
        }
        if (!filter.maxPrice().isEmpty()) {
            GoDecimal.parse(filter.maxPrice()).ifPresent(maxPrice -> {
                conditions.add("price <= ?");
                args.add(maxPrice);
            });
        }

        if (conditions.isEmpty()) {
            return new Where("", List.of());
        }
        return new Where(" WHERE " + String.join(" AND ", conditions), args);
    }

    /** The ORDER BY clause; the column comes from an allow-list, so it is safe to put into the query. */
    static String orderBy(ProductFilter filter) {
        String sortBy = ALLOWED_SORT_FIELDS.contains(filter.sortBy()) ? filter.sortBy() : "created_at";
        String sortOrder = "asc".equals(filter.sortOrder()) ? "ASC" : "DESC";
        // id breaks ties, so products with equal values are neither repeated nor skipped across pages.
        return " ORDER BY " + sortBy + " " + sortOrder + ", id " + sortOrder;
    }

    @Override
    public Optional<Product> findById(UUID id) {
        String cacheKey = CacheKey.PRODUCT.formatted(id);

        Optional<Product> cached = cache.get(cacheKey, Product.class);
        if (cached.isPresent()) {
            return cached;
        }

        Optional<Product> product = jdbc.sql("SELECT " + COLUMNS + " FROM products WHERE id = ?")
                .param(id)
                .query(RowMappers.PRODUCT)
                .optional();
        product.ifPresent(p -> cache.set(cacheKey, p, CacheKey.TTL_PRODUCT));
        return product;
    }

    @Override
    public Product update(Product product) {
        Product stored;
        try {
            stored = jdbc.sql("""
                            UPDATE products
                            SET category_id = ?, name = ?, description = ?, price = ?, image_url = ?, updated_at = NOW()
                            WHERE id = ?
                            RETURNING %s""".formatted(COLUMNS))
                    .params(product.getCategoryId(), product.getName(), product.getDescription(), product.getPrice(),
                            product.getImageUrl(), product.getId())
                    .query(RowMappers.PRODUCT)
                    .single();
        } catch (DataAccessException e) {
            throw DbErrors.translate(e);
        }
        cache.delete(CacheKey.PRODUCT.formatted(product.getId()));
        return stored;
    }

    @Override
    public void delete(UUID id) {
        try {
            jdbc.sql("DELETE FROM products WHERE id = ?").param(id).update();
        } catch (DataAccessException e) {
            throw DbErrors.translate(e);
        }
        cache.delete(CacheKey.PRODUCT.formatted(id));
    }

    @Override
    public void updateStock(UUID id, int quantity) {
        try {
            jdbc.sql("UPDATE products SET stock = ?, updated_at = NOW() WHERE id = ?")
                    .params(quantity, id)
                    .update();
        } catch (DataAccessException e) {
            throw DbErrors.translate(e);
        }
        cache.delete(CacheKey.PRODUCT.formatted(id));
    }
}
