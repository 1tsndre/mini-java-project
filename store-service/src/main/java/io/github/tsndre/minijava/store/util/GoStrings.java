package io.github.tsndre.minijava.store.util;

public final class GoStrings {

    private GoStrings() {
    }

    /**
     * Lower-cases like Go's {@code strings.ToLower}: one code point at a time, without the
     * locale and context rules of {@link String#toLowerCase} (e.g. "İ" becomes "i", not "i̇").
     */
    public static String toLower(String s) {
        StringBuilder out = new StringBuilder(s.length());
        s.codePoints().map(Character::toLowerCase).forEach(out::appendCodePoint);
        return out.toString();
    }

    /** The number of characters as Go counts runes, which is what a VARCHAR column limits. */
    public static int runeCount(String s) {
        return s.codePointCount(0, s.length());
    }
}
