package io.github.tsndre.minijava.store.grpc;

import io.github.tsndre.minijava.proto.payment.GetPaymentStatusRequest;
import io.github.tsndre.minijava.proto.payment.GetPaymentStatusResponse;
import io.github.tsndre.minijava.proto.payment.PaymentServiceGrpc;
import io.github.tsndre.minijava.store.config.AppConfig;
import io.github.tsndre.minijava.store.dto.response.PaymentStatusResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** The payment service's gRPC API; the connection is opened lazily on the first call. */
@Slf4j
@Component
public class GrpcPaymentClient implements PaymentStatusClient, DisposableBean {

    private final ManagedChannel channel;
    private final PaymentServiceGrpc.PaymentServiceBlockingStub stub;
    /** A call gets as long as the request it serves, like the Go client's request context. */
    private final Duration deadline;

    public GrpcPaymentClient(AppConfig config) {
        this.channel = ManagedChannelBuilder.forTarget(config.payment().grpcAddr()).usePlaintext().build();
        this.stub = PaymentServiceGrpc.newBlockingStub(channel);
        this.deadline = config.app().requestTimeout();
        log.atInfo().addKeyValue("addr", config.payment().grpcAddr()).log("payment gRPC client ready");
    }

    @Override
    public PaymentStatusResponse getStatus(String orderId) {
        GetPaymentStatusResponse resp = stub
                .withDeadlineAfter(deadline.toNanos(), TimeUnit.NANOSECONDS)
                .getPaymentStatus(GetPaymentStatusRequest.newBuilder().setOrderId(orderId).build());
        return new PaymentStatusResponse(resp.getOrderId(), resp.getPaymentId(), resp.getStatus(), resp.getAmount(),
                resp.getMethod());
    }

    @Override
    public void destroy() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
}
