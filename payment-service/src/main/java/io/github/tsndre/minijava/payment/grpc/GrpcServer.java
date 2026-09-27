package io.github.tsndre.minijava.payment.grpc;

import io.github.tsndre.minijava.payment.config.AppConfig;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.TimeUnit;

/** Serves the payment gRPC API; on shutdown, in-flight calls are allowed to finish. */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrpcServer implements SmartLifecycle {

    /** Stops after the order consumer, like the Go service. */
    private static final int PHASE = DEFAULT_PHASE - 2048;
    private static final long GRACEFUL_STOP_SECONDS = 30;

    private final AppConfig config;
    private final PaymentGrpcHandler handler;

    private Server server;

    @Override
    public void start() {
        int port = Integer.parseInt(config.grpcPort());
        server = Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create())
                .addService(handler)
                .build();
        try {
            server.start();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to listen on gRPC port", e);
        }
        log.atInfo().addKeyValue("port", config.grpcPort()).log("payment gRPC server starting");
    }

    @Override
    public void stop() {
        if (server == null) {
            return;
        }
        server.shutdown();
        try {
            if (!server.awaitTermination(GRACEFUL_STOP_SECONDS, TimeUnit.SECONDS)) {
                server.shutdownNow();
            }
        } catch (InterruptedException e) {
            server.shutdownNow();
            Thread.currentThread().interrupt();
        }
        server = null;
        log.info("payment service stopped");
    }

    @Override
    public boolean isRunning() {
        return server != null;
    }

    @Override
    public int getPhase() {
        return PHASE;
    }
}
