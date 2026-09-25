package io.github.tsndre.minijava.store.util;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Parses UUIDs with the same rules as Go's github.com/google/uuid Parse: canonical form,
 * braces, the urn:uuid: prefix and 32 plain hex digits are accepted, case-insensitively.
 * {@link UUID#fromString} is not used because it also accepts malformed input such as "1-1-1-1-1".
 * Like Go, the rules apply to the UTF-8 bytes of the text.
 */
public final class Uuids {

    private static final byte[] URN_PREFIX = "urn:uuid:".getBytes(StandardCharsets.US_ASCII);
    /** Where each of the 16 bytes starts in the canonical xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx form. */
    private static final int[] BYTE_OFFSETS = {0, 2, 4, 6, 9, 11, 14, 16, 19, 21, 24, 26, 28, 30, 32, 34};

    private Uuids() {
    }

    public static Optional<UUID> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        byte[] s = value.getBytes(StandardCharsets.UTF_8);
        int start;
        switch (s.length) {
            case 36 -> start = 0;
            case 36 + 9 -> {
                for (int i = 0; i < URN_PREFIX.length; i++) {
                    if (Character.toLowerCase(s[i]) != URN_PREFIX[i]) {
                        return Optional.empty();
                    }
                }
                start = URN_PREFIX.length;
            }
            // Like Go, the enclosing characters themselves are not checked.
            case 36 + 2 -> start = 1;
            case 32 -> {
                return fromHex(s, 0, i -> 2 * i);
            }
            default -> {
                return Optional.empty();
            }
        }
        if (s[start + 8] != '-' || s[start + 13] != '-' || s[start + 18] != '-' || s[start + 23] != '-') {
            return Optional.empty();
        }
        return fromHex(s, start, i -> BYTE_OFFSETS[i]);
    }

    private static Optional<UUID> fromHex(byte[] s, int start, java.util.function.IntUnaryOperator offset) {
        long high = 0;
        long low = 0;
        for (int i = 0; i < 16; i++) {
            int at = start + offset.applyAsInt(i);
            int hi = hexValue(s[at]);
            int lo = hexValue(s[at + 1]);
            if (hi < 0 || lo < 0) {
                return Optional.empty();
            }
            long b = (hi << 4) | lo;
            if (i < 8) {
                high = (high << 8) | b;
            } else {
                low = (low << 8) | b;
            }
        }
        return Optional.of(new UUID(high, low));
    }

    private static int hexValue(byte c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return -1;
    }
}
