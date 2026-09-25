package io.github.tsndre.minijava.store.dto.request;

/** Missing fields read as empty strings and zeros, like Go's zero values. */
public record UpdateStoreRequest(
        String name,
        String description) {

    public UpdateStoreRequest {
        name = name == null ? "" : name;
        description = description == null ? "" : description;
    }
}
