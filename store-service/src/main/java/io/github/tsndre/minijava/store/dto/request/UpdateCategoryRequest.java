package io.github.tsndre.minijava.store.dto.request;

/** Missing fields read as empty strings and zeros, like Go's zero values. */
public record UpdateCategoryRequest(
        String name) {

    public UpdateCategoryRequest {
        name = name == null ? "" : name;
    }
}
