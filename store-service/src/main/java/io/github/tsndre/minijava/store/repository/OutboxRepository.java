package io.github.tsndre.minijava.store.repository;

import io.github.tsndre.minijava.store.model.OutboxMessage;

import java.util.Optional;
import java.util.function.Consumer;

/** Holds the messages written together with the changes they announce, until the relay publishes them. */
public interface OutboxRepository {

    /**
     * Hands up to {@code limit} queued messages, oldest first, to {@code publish} and deletes each one
     * it accepts. It stops at the first one {@code publish} rejects by throwing, which stays queued
     * with the rest. Messages another relay is publishing are skipped, so several store-service
     * instances can relay at once.
     */
    PublishResult publishPending(int limit, Consumer<OutboxMessage> publish);

    /** How many messages were published, and why the next one was not, if one was rejected. */
    record PublishResult(int published, Optional<RuntimeException> rejection) {
    }
}
