package com.retap.notificationconsumer.domain;

public record FailedNotificationMessage(
        NotificationMessage message,
        int retryCount,
        String errorMessage
) {

    public FailedNotificationMessage nextRetry(String nextErrorMessage) {
        return new FailedNotificationMessage(message, retryCount + 1, nextErrorMessage);
    }
}
