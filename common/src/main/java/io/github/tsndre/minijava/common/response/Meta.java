package io.github.tsndre.minijava.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record Meta(
        String requestId,
        String timestamp,
        @JsonInclude(JsonInclude.Include.NON_NULL) Pagination pagination) {

    /** Meta for a response sent now, timestamped in UTC to the second (RFC 3339). */
    public static Meta of(String requestId) {
        String timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
        return new Meta(requestId == null ? "" : requestId, timestamp, null);
    }

    public Meta withPagination(Pagination pagination) {
        return new Meta(requestId, timestamp, pagination);
    }
}
