package io.github.tsndre.minijava.store.web.bind;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Binds a UUID path variable; a value that is not a UUID is answered with 400 and {@link #message}. */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface PathUuid {

    String value();

    /** The error message when the value is not a UUID, e.g. "invalid store id". */
    String message();
}
