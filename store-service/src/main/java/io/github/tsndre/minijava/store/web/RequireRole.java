package io.github.tsndre.minijava.store.web;

import io.github.tsndre.minijava.store.constant.Role;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The authenticated user must have one of these roles; checked after {@link Authenticated}. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    Role[] value();
}
