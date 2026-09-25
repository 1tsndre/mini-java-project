package io.github.tsndre.minijava.store.dto.request;

/** Missing fields read as empty strings and zeros, like Go's zero values. */
public record CreateReviewRequest(
        Long rating,
        String comment) {

    public CreateReviewRequest {
        rating = rating == null ? 0L : rating;
        comment = comment == null ? "" : comment;
    }
}
