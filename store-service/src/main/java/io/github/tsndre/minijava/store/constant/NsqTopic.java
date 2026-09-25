package io.github.tsndre.minijava.store.constant;

public final class NsqTopic {

    public static final String ORDER_CREATED = "order.created";
    public static final String PAYMENT_SUCCESS = "payment.success";
    public static final String PAYMENT_FAILED = "payment.failed";

    public static final String CHANNEL_PAYMENT_SERVICE = "payment-service";
    public static final String CHANNEL_STORE_SERVICE = "store-service";

    private NsqTopic() {
    }
}
