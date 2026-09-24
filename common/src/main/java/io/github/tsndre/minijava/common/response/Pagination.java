package io.github.tsndre.minijava.common.response;

public record Pagination(long currentPage, int perPage, long totalItems, long totalPages) {
}
