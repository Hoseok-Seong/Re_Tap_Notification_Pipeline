package com.retap.notificationproducer.domain;

public record NotificationSendResult(
        long publishedCount,
        long elapsedMillis,
        double throughputPerSecond
) {
}
