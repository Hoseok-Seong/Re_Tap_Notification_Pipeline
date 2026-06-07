package com.retap.fcmmock.service;

import com.retap.fcmmock.config.FcmMockProperties;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

@Service
public class FcmMockService {

    private final FcmMockProperties properties;
    private final FcmMockMetrics metrics;

    public FcmMockService(FcmMockProperties properties, FcmMockMetrics metrics) {
        this.properties = properties;
        this.metrics = metrics;
    }

    public SendResult send(String projectId) {
        metrics.incrementTotalRequests();
        delay();

        if (shouldFail()) {
            metrics.incrementFailureRequests();
            return SendResult.failure("mock-fcm-send-failed");
        }

        metrics.incrementSuccessRequests();
        return SendResult.success("projects/%s/messages/%s".formatted(projectId, UUID.randomUUID()));
    }

    public FcmMockMetrics.Snapshot metricsSnapshot() {
        return metrics.snapshot(properties.responseDelayMs(), properties.failureRatePercent());
    }

    private void delay() {
        if (properties.responseDelayMs() == 0) {
            return;
        }

        try {
            Thread.sleep(properties.responseDelayMs());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while delaying FCM mock response", e);
        }
    }

    private boolean shouldFail() {
        return ThreadLocalRandom.current().nextDouble(100.0) < properties.failureRatePercent();
    }

    public record SendResult(boolean success, String value) {

        public static SendResult success(String messageName) {
            return new SendResult(true, messageName);
        }

        public static SendResult failure(String errorMessage) {
            return new SendResult(false, errorMessage);
        }
    }
}
