package io.github.tsndre.minijava.common.messaging;

import com.sproutsocial.nsq.Client;
import com.sproutsocial.nsq.ListBasedBalanceStrategy;
import com.sproutsocial.nsq.Publisher;

import java.util.List;

public final class NsqPublishers {

    private NsqPublishers() {
    }

    /**
     * A publisher for a single nsqd. A publish while nsqd is unreachable fails right away, and the
     * next publish tries to reconnect, instead of nsq-j's default of blocking every publisher for
     * a failover period.
     */
    public static Publisher create(String nsqdAddr) {
        Publisher publisher = new Publisher(Client.getDefaultClient(),
                ListBasedBalanceStrategy.getFailoverStrategyBuilder(List.of(nsqdAddr)));
        publisher.setFailoverDurationSecs(0);
        return publisher;
    }
}
