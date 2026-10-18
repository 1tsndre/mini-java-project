package io.github.tsndre.minijava.store.worker;

import io.github.tsndre.minijava.store.model.OutboxMessage;
import io.github.tsndre.minijava.store.repository.OutboxRepository;
import io.github.tsndre.minijava.store.service.MessagePublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayWorkerTest {

    private static final OutboxMessage MESSAGE =
            new OutboxMessage(7, "order.created", "{\"order_id\":\"o-1\"}", OffsetDateTime.now());

    @Mock
    private OutboxRepository outboxRepository;

    private static final class RecordingPublisher implements MessagePublisher {

        private final List<String> published = new ArrayList<>();
        private RuntimeException error;

        @Override
        public void publish(String topic, byte[] body) {
            if (error != null) {
                throw error;
            }
            published.add(topic + " " + new String(body, StandardCharsets.UTF_8));
        }
    }

    /** The outbox hands MESSAGE to the relay's callback and records what the callback threw. */
    private List<RuntimeException> outboxHoldsOneMessage() {
        List<RuntimeException> rejections = new ArrayList<>();
        when(outboxRepository.publishPending(eq(100), any())).thenAnswer(invocation -> {
            Consumer<OutboxMessage> publish = invocation.getArgument(1);
            try {
                publish.accept(MESSAGE);
            } catch (RuntimeException e) {
                rejections.add(e);
                return new OutboxRepository.PublishResult(0, Optional.of(e));
            }
            return new OutboxRepository.PublishResult(1, Optional.empty());
        });
        return rejections;
    }

    @Test
    void publishesTheQueuedMessage() {
        RecordingPublisher publisher = new RecordingPublisher();
        List<RuntimeException> rejections = outboxHoldsOneMessage();

        new OutboxRelayWorker(outboxRepository, publisher).runOnce();

        assertThat(publisher.published).containsExactly("order.created {\"order_id\":\"o-1\"}");
        assertThat(rejections).isEmpty();
    }

    @Test
    void reportsARejectedPublishToTheOutbox() {
        RecordingPublisher publisher = new RecordingPublisher();
        publisher.error = new IllegalStateException("nsqd down");
        List<RuntimeException> rejections = outboxHoldsOneMessage();

        new OutboxRelayWorker(outboxRepository, publisher).runOnce();

        assertThat(publisher.published).isEmpty();
        assertThat(rejections).containsExactly(publisher.error);
    }

    @Test
    void aFailingOutboxDoesNotStopTheRelay() {
        when(outboxRepository.publishPending(eq(100), any())).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> new OutboxRelayWorker(outboxRepository, new RecordingPublisher()).runOnce())
                .doesNotThrowAnyException();
    }
}
