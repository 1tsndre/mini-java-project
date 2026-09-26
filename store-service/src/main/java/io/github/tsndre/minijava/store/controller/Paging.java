package io.github.tsndre.minijava.store.controller;

import io.github.tsndre.minijava.common.response.ApiResponse;
import io.github.tsndre.minijava.common.response.Meta;
import io.github.tsndre.minijava.common.response.Pagination;
import io.github.tsndre.minijava.common.response.Responses;
import io.github.tsndre.minijava.store.pagination.Pages;
import io.github.tsndre.minijava.store.repository.PageResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

final class Paging {

    private Paging() {
    }

    /** A 200 with the page's items and its pagination block, for the page as requested (before normalizing). */
    static ResponseEntity<ApiResponse> respond(PageResult<?> result, long page, long perPage, Meta meta) {
        Pages.Page normalized = Pages.normalize(page, perPage);
        return Responses.successWithPagination(HttpStatus.OK, result.items(), meta, new Pagination(
                normalized.page(),
                normalized.perPage(),
                result.total(),
                Pages.totalPages(result.total(), normalized.perPage())));
    }
}
