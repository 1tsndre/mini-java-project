package io.github.tsndre.minijava.store.dto.request;

/** Every field is optional; a null stock means it is left unchanged. */
public record UpdateProductRequest(
        String categoryId,
        String name,
        String description,
        String price,
        Long stock) {

    public UpdateProductRequest {
        categoryId = categoryId == null ? "" : categoryId;
        name = name == null ? "" : name;
        description = description == null ? "" : description;
        price = price == null ? "" : price;
    }
}
