package io.github.tsndre.minijava.store.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Optional;

/**
 * Parses and prints decimals like the Go service's shopspring/decimal, so prices are accepted
 * and shown the same way: "1e3", ".5" and "-.5" parse, and printing drops trailing zeros.
 */
public final class GoDecimal {

    private GoDecimal() {
    }

    /** Parses like {@code decimal.NewFromString}; empty when the text is not a decimal. */
    public static Optional<BigDecimal> parse(String value) {
        String s = value;
        long exp = 0;

        int e = indexOfExponent(s);
        if (e >= 0) {
            Optional<Long> exponent = parseInteger(s.substring(e + 1), Integer.MIN_VALUE, Integer.MAX_VALUE);
            if (exponent.isEmpty()) {
                return Optional.empty();
            }
            s = s.substring(0, e);
            exp = exponent.get();
        }

        int point = -1;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '.') {
                if (point > -1) {
                    return Optional.empty();
                }
                point = i;
            }
        }

        String digits = s;
        if (point >= 0) {
            digits = s.substring(0, point) + s.substring(point + 1);
            exp -= s.length() - point - 1;
        }
        if (!isInteger(digits)) {
            return Optional.empty();
        }

        long scale = -exp;
        if (scale < Integer.MIN_VALUE || scale > Integer.MAX_VALUE) {
            return Optional.empty();
        }
        return Optional.of(new BigDecimal(new BigInteger(digits), (int) scale));
    }

    /** Prints like {@code decimal.Decimal.String}: plain notation without trailing fractional zeros. */
    public static String format(BigDecimal value) {
        if (value.signum() == 0) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private static int indexOfExponent(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == 'e' || s.charAt(i) == 'E') {
                return i;
            }
        }
        return -1;
    }

    /** An optional sign followed by at least one ASCII digit, as Go's strconv and math/big accept. */
    private static boolean isInteger(String s) {
        int start = !s.isEmpty() && (s.charAt(0) == '+' || s.charAt(0) == '-') ? 1 : 0;
        if (start == s.length()) {
            return false;
        }
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static Optional<Long> parseInteger(String s, long min, long max) {
        if (!isInteger(s)) {
            return Optional.empty();
        }
        try {
            long value = new BigInteger(s).longValueExact();
            return value < min || value > max ? Optional.empty() : Optional.of(value);
        } catch (ArithmeticException e) {
            return Optional.empty();
        }
    }
}
