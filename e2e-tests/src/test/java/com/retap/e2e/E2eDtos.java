package com.retap.e2e;

record MetricsResponse(
        long totalRequests,
        long successRequests,
        long failureRequests,
        long responseDelayMs,
        double failureRatePercent
) {
}

record ProducerResponse(
        long publishedCount,
        long elapsedMillis,
        double throughputPerSecond
) {
}
