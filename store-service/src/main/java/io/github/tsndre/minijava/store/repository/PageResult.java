package io.github.tsndre.minijava.store.repository;

import java.util.List;

public record PageResult<T>(List<T> items, long total) {
}
