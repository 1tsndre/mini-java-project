package io.github.tsndre.minijava.store.service;

/** Publishes a message to a message-queue topic; the order service only needs this much of NSQ. */
public interface MessagePublisher {

    /**
     * @throws RuntimeException if the message could not be published
     */
    void publish(String topic, byte[] body);
}
