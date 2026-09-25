package io.github.tsndre.minijava.store.constant;

public final class ValidationLimit {

    /**
     * The size of the VARCHAR(255) columns (emails and names). Longer input has to be rejected
     * up front: PostgreSQL would refuse it and the request would end as a 500 instead of a 400.
     */
    public static final int MAX_VARCHAR_LENGTH = 255;
    /** The longest password bcrypt accepts. */
    public static final int MAX_PASSWORD_BYTES = 72;
    /** The minimum password length, counted in bytes like the Go service. */
    public static final int MIN_PASSWORD_BYTES = 6;

    private ValidationLimit() {
    }
}
