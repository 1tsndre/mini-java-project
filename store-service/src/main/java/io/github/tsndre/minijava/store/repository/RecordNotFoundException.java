package io.github.tsndre.minijava.store.repository;

public class RecordNotFoundException extends RuntimeException {

    public RecordNotFoundException() {
        super("record not found");
    }
}
