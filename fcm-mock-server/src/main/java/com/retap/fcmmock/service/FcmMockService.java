package com.retap.fcmmock.service;

import com.retap.fcmmock.config.FcmMockProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

@Service
public class FcmMockService {

    public static final int MAX_BATCH_SIZE = 500;

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

    public BatchSendResult sendBatch(String projectId, int messageCount) {
        if (messageCount < 1 || messageCount > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("Batch message count must be between 1 and " + MAX_BATCH_SIZE);
        }

        metrics.addTotalRequests(messageCount);
        delay();

        List<SendResult> responses = new ArrayList<>(messageCount);
        long successCount = 0;
        long failureCount = 0;

        for (int i = 0; i < messageCount; i++) {
            if (shouldFail()) {
                responses.add(SendResult.failure("mock-fcm-send-failed"));
                failureCount++;
            } else {
                responses.add(SendResult.success("projects/%s/messages/%s".formatted(projectId, UUID.randomUUID())));
                successCount++;
            }
        }

        metrics.addSuccessRequests(successCount);
        metrics.addFailureRequests(failureCount);

        return new BatchSendResult(successCount, failureCount, responses);
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

    public record BatchSendResult(long successCount, long failureCount, List<SendResult> responses) {
    }
}
