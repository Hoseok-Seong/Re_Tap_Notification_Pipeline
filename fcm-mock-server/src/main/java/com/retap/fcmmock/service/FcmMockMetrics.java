package com.retap.fcmmock.service;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class FcmMockMetrics {

    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong successRequests = new AtomicLong();
    private final AtomicLong failureRequests = new AtomicLong();

    public long incrementTotalRequests() {
        return totalRequests.incrementAndGet();
    }

    public long incrementSuccessRequests() {
        return successRequests.incrementAndGet();
    }

    public long incrementFailureRequests() {
        return failureRequests.incrementAndGet();
    }

    public Snapshot snapshot(long responseDelayMs, double failureRatePercent) {
        return new Snapshot(
                totalRequests.get(),
                successRequests.get(),
                failureRequests.get(),
                responseDelayMs,
                failureRatePercent
        );
    }

    public record Snapshot(
            long totalRequests,
            long successRequests,
            long failureRequests,
            long responseDelayMs,
            double failureRatePercent
    ) {
    }
}
