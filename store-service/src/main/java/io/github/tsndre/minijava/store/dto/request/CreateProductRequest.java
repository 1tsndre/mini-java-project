package io.github.tsndre.minijava.store.dto.request;

/** Numbers are read as long, as Go's 64-bit int, so an oversized stock gets a validation message instead of a parse error. */
public record CreateProductRequest(
        String categoryId,
        String name,
        String description,
        String price,
        Long stock) {

    public CreateProductRequest {
        categoryId = categoryId == null ? "" : categoryId;
        name = name == null ? "" : name;
        description = description == null ? "" : description;
        price = price == null ? "" : price;
        stock = stock == null ? 0L : stock;
    }
}
