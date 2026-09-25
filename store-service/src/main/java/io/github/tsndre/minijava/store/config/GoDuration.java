package io.github.tsndre.minijava.store.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Parses and prints durations in Go's format ("15m", "1h30m", "500ms"), so the same .env values
 * work for both the Go and the Java service and error messages read the same.
 */
public final class GoDuration {

    private static final long NANOS_PER_MICRO = 1_000L;
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long NANOS_PER_MINUTE = 60 * NANOS_PER_SECOND;
    private static final long NANOS_PER_HOUR = 60 * NANOS_PER_MINUTE;

    private static final Map<String, Long> UNITS = Map.of(
            "ns", 1L,
            "us", NANOS_PER_MICRO,
            "µs", NANOS_PER_MICRO,
            "μs", NANOS_PER_MICRO,
            "ms", NANOS_PER_MILLI,
            "s", NANOS_PER_SECOND,
            "m", NANOS_PER_MINUTE,
            "h", NANOS_PER_HOUR);

    private GoDuration() {
    }

    /** 2^63 as an unsigned 64-bit value, the bound Go checks against. */
    private static final long TWO_POW_63 = Long.MIN_VALUE;

    /**
     * Parses a Go duration string such as "300ms", "-1.5h" or "2h45m", following Go's
     * time.ParseDuration step by step, including its unsigned overflow checks and its float
     * arithmetic for fractions.
     */
    public static Duration parse(String value) {
        String s = value;
        long d = 0;
        boolean negative = false;

        if (!s.isEmpty() && (s.charAt(0) == '-' || s.charAt(0) == '+')) {
            negative = s.charAt(0) == '-';
            s = s.substring(1);
        }
        if (s.equals("0")) {
            return Duration.ZERO;
        }
        if (s.isEmpty()) {
            throw invalid(value);
        }

        while (!s.isEmpty()) {
            if (!(s.charAt(0) == '.' || isDigit(s.charAt(0)))) {
                throw invalid(value);
            }

            int i = 0;
            long v = 0;
            for (; i < s.length() && isDigit(s.charAt(i)); i++) {
                if (Long.compareUnsigned(v, Long.divideUnsigned(TWO_POW_63, 10)) > 0) {
                    throw invalid(value);
                }
                v = v * 10 + (s.charAt(i) - '0');
                if (Long.compareUnsigned(v, TWO_POW_63) > 0) {
                    throw invalid(value);
                }
            }
            boolean pre = i > 0;
            s = s.substring(i);

            boolean post = false;
            long f = 0;
            double scale = 1;
            if (!s.isEmpty() && s.charAt(0) == '.') {
                s = s.substring(1);
                boolean overflow = false;
                int j = 0;
                for (; j < s.length() && isDigit(s.charAt(j)); j++) {
                    if (overflow) {
                        continue;
                    }
                    if (Long.compareUnsigned(f, Long.MAX_VALUE / 10) > 0) {
                        overflow = true;
                        continue;
                    }
                    long y = f * 10 + (s.charAt(j) - '0');
                    if (Long.compareUnsigned(y, TWO_POW_63) > 0) {
                        overflow = true;
                        continue;
                    }
                    f = y;
                    scale *= 10;
                }
                post = j > 0;
                s = s.substring(j);
            }
            if (!pre && !post) {
                // No digits, e.g. ".s" or "-.s".
                throw invalid(value);
            }

            int u = 0;
            while (u < s.length() && s.charAt(u) != '.' && !isDigit(s.charAt(u))) {
                u++;
            }
            if (u == 0) {
                throw new IllegalArgumentException("time: missing unit in duration " + quote(value));
            }
            String unitName = s.substring(0, u);
            s = s.substring(u);
            Long unit = UNITS.get(unitName);
            if (unit == null) {
                throw new IllegalArgumentException(
                        "time: unknown unit " + quote(unitName) + " in duration " + quote(value));
            }
            if (Long.compareUnsigned(v, Long.divideUnsigned(TWO_POW_63, unit)) > 0) {
                throw invalid(value);
            }
            v *= unit;
            if (f != 0) {
                // Go uses float64 here to be nanosecond accurate for fractions of hours.
                v += (long) (unsignedToDouble(f) * ((double) unit / scale));
                if (Long.compareUnsigned(v, TWO_POW_63) > 0) {
                    throw invalid(value);
                }
            }
            d += v;
            if (Long.compareUnsigned(d, TWO_POW_63) > 0) {
                throw invalid(value);
            }
        }

        if (negative) {
            return Duration.ofNanos(-d);
        }
        if (d < 0) {
            throw invalid(value);
        }
        return Duration.ofNanos(d);
    }

    private static double unsignedToDouble(long value) {
        return value >= 0 ? value : ((value >>> 1) | (value & 1)) * 2.0;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /** Prints a duration the way Go's Duration.String does, e.g. "35s", "1m0s", "1h30m0s", "250ms". */
    public static String format(Duration duration) {
        long nanos = duration.toNanos();
        if (nanos == 0) {
            return "0s";
        }
        String sign = nanos < 0 ? "-" : "";
        long u = Math.abs(nanos);

        if (u < NANOS_PER_SECOND) {
            if (u < NANOS_PER_MICRO) {
                return sign + u + "ns";
            }
            if (u < NANOS_PER_MILLI) {
                return sign + fraction(u, NANOS_PER_MICRO) + "µs";
            }
            return sign + fraction(u, NANOS_PER_MILLI) + "ms";
        }

        long hours = u / NANOS_PER_HOUR;
        long minutes = (u % NANOS_PER_HOUR) / NANOS_PER_MINUTE;
        String seconds = fraction(u % NANOS_PER_MINUTE, NANOS_PER_SECOND) + "s";
        if (hours > 0) {
            return sign + hours + "h" + minutes + "m" + seconds;
        }
        if (minutes > 0) {
            return sign + minutes + "m" + seconds;
        }
        return sign + seconds;
    }

    private static String fraction(long value, long unit) {
        String whole = Long.toString(value / unit);
        long rest = value % unit;
        if (rest == 0) {
            return whole;
        }
        String digits = String.format("%0" + (Long.toString(unit).length() - 1) + "d", rest);
        return whole + "." + digits.replaceFirst("0+$", "");
    }

    private static IllegalArgumentException invalid(String value) {
        return new IllegalArgumentException("time: invalid duration " + quote(value));
    }

    /**
     * Quotes like Go's time package: non-ASCII and control characters as \\x escapes of their
     * UTF-8 bytes, quotes and backslashes escaped.
     */
    private static String quote(String s) {
        StringBuilder out = new StringBuilder("\"");
        s.codePoints().forEach(c -> {
            if (c >= 0x80 || c < ' ') {
                for (byte b : new String(Character.toChars(c)).getBytes(StandardCharsets.UTF_8)) {
                    out.append(String.format("\\x%02x", b & 0xff));
                }
            } else {
                if (c == '"' || c == '\\') {
                    out.append('\\');
                }
                out.appendCodePoint(c);
            }
        });
        return out.append('"').toString();
    }
}
