package io.github.tsndre.minijava.store.messaging;

import com.sproutsocial.nsq.Publisher;
import io.github.tsndre.minijava.common.messaging.NsqPublishers;
import io.github.tsndre.minijava.store.config.AppConfig;
import io.github.tsndre.minijava.store.service.MessagePublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/** Publishes to nsqd; the connection is opened on the first publish. */
@Slf4j
@Component
public class NsqMessagePublisher implements MessagePublisher, DisposableBean {

    private final Publisher publisher;

    public NsqMessagePublisher(AppConfig config) {
        this.publisher = NsqPublishers.create(config.nsq().nsqdAddr());
        log.info("connected to NSQ");
    }

    @Override
    public void publish(String topic, byte[] body) {
        publisher.publish(topic, body);
    }

    @Override
    public void destroy() {
        publisher.stop();
    }
}
