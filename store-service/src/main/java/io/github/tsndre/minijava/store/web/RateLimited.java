package io.github.tsndre.minijava.store.web;

import io.github.tsndre.minijava.store.constant.RateLimitKey;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The route is rate limited per user (or per IP before login) by each of these limits, applied in
 * order and after authentication.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

    RateLimitKey[] value();
}
