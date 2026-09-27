package io.github.tsndre.minijava.payment.grpc;

import io.github.tsndre.minijava.payment.constant.PaymentStatus;
import io.github.tsndre.minijava.payment.service.PaymentRecord;
import io.github.tsndre.minijava.payment.service.PaymentResult;
import io.github.tsndre.minijava.payment.service.PaymentService;
import io.github.tsndre.minijava.proto.payment.GetPaymentStatusRequest;
import io.github.tsndre.minijava.proto.payment.GetPaymentStatusResponse;
import io.github.tsndre.minijava.proto.payment.PaymentServiceGrpc;
import io.github.tsndre.minijava.proto.payment.ProcessPaymentRequest;
import io.github.tsndre.minijava.proto.payment.ProcessPaymentResponse;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentGrpcHandler extends PaymentServiceGrpc.PaymentServiceImplBase {

    private final PaymentService paymentService;

    @Override
    public void processPayment(ProcessPaymentRequest req, StreamObserver<ProcessPaymentResponse> responseObserver) {
        PaymentResult result = paymentService.processPayment(req.getOrderId(), req.getAmount(), req.getMethod());
        responseObserver.onNext(ProcessPaymentResponse.newBuilder()
                .setSuccess(result.success())
                .setPaymentId(result.paymentId())
                .setStatus((result.success() ? PaymentStatus.SUCCESS : PaymentStatus.FAILED).value())
                .setMessage(result.message())
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getPaymentStatus(GetPaymentStatusRequest req, StreamObserver<GetPaymentStatusResponse> responseObserver) {
        Optional<PaymentRecord> record = paymentService.getStatus(req.getOrderId());
        GetPaymentStatusResponse response = record
                .map(rec -> GetPaymentStatusResponse.newBuilder()
                        .setPaymentId(rec.paymentId())
                        .setOrderId(rec.orderId())
                        .setStatus(rec.status().value())
                        .setAmount(rec.amount())
                        .setMethod(rec.method())
                        .build())
                .orElseGet(() -> GetPaymentStatusResponse.newBuilder()
                        .setOrderId(req.getOrderId())
                        .setStatus(PaymentStatus.NOT_FOUND.value())
                        .build());
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
