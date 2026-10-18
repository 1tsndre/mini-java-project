package io.github.tsndre.minijava.store.model;

import java.time.OffsetDateTime;

/** A message waiting in the outbox for the relay to publish it. */
public record OutboxMessage(long id, String topic, String payload, OffsetDateTime createdAt) {
}
