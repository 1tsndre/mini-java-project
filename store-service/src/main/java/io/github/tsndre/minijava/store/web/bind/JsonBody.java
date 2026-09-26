package io.github.tsndre.minijava.store.web.bind;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a parameter to the JSON request body, decoded the way the Go service's encoding/json
 * decoder reads it: the Content-Type is ignored, unknown fields are ignored, field names match
 * case-insensitively, a JSON null gives an empty request, and only the first JSON value is read.
 * Any decoding failure is answered with 400 "invalid request body".
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonBody {
}
