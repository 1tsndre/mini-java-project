package io.github.tsndre.minijava.store.model;

/** Query parameters of the product list; every filter is optional (empty string). */
public record ProductFilter(
        String categoryId,
        String storeId,
        String search,
        String minPrice,
        String maxPrice,
        String sortBy,
        String sortOrder,
        long page,
        long perPage) {

    public ProductFilter withPage(long page, long perPage) {
        return new ProductFilter(categoryId, storeId, search, minPrice, maxPrice, sortBy, sortOrder, page, perPage);
    }
}
