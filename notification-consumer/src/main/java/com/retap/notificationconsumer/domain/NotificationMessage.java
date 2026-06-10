package com.retap.notificationconsumer.domain;

public record NotificationMessage(
        String messageId,
        long userId,
        String fcmToken,
        String title,
        String body,
        String createdAt
) {
}
