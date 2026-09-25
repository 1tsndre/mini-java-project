package io.github.tsndre.minijava.store.pagination;

public final class Pages {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PER_PAGE = 10;
    private static final int MAX_PER_PAGE = 100;

    /** A normalized page request; {@code page} is a long because the Go service reads it as a 64-bit int. */
    public record Page(long page, int perPage) {
    }

    private Pages() {
    }

    public static Page normalize(long page, long perPage) {
        if (page <= 0) {
            page = DEFAULT_PAGE;
        }
        if (perPage <= 0) {
            perPage = DEFAULT_PER_PAGE;
        }
        if (perPage > MAX_PER_PAGE) {
            perPage = MAX_PER_PAGE;
        }
        return new Page(page, (int) perPage);
    }

    public static long totalPages(long total, int perPage) {
        long pages = total / perPage;
        if (total % perPage != 0) {
            pages++;
        }
        return pages;
    }

    /**
     * Reads a page query parameter the way the Go handlers do with {@code strconv.Atoi}, ignoring
     * the error: anything that is not an integer counts as 0 (the default), and an integer out of
     * range is clamped to the nearest 64-bit limit.
     */
    public static long parse(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        int i = 0;
        boolean negative = false;
        if (value.charAt(0) == '+' || value.charAt(0) == '-') {
            negative = value.charAt(0) == '-';
            i = 1;
        }
        if (i == value.length()) {
            return 0;
        }
        long result = 0;
        boolean overflow = false;
        for (; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return 0;
            }
            if (!overflow) {
                int digit = c - '0';
                // Accumulate as a negative number, which can hold Long.MIN_VALUE.
                if (result < (Long.MIN_VALUE + digit) / 10) {
                    overflow = true;
                } else {
                    result = result * 10 - digit;
                }
            }
        }
        if (overflow) {
            return negative ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
        if (!negative) {
            return result == Long.MIN_VALUE ? Long.MAX_VALUE : -result;
        }
        return result;
    }
}
