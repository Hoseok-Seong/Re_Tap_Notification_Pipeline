package com.retap.notificationconsumer.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "notification.consumer")
public record NotificationConsumerProperties(
        @NotBlank String topic,
        @NotBlank String dltTopic,
        @Min(1) int maxRetryCount,
        @Min(1) long retryBackoffMillis,
        @NotBlank String fcmBaseUrl,
        @Min(1) int fcmConnectTimeoutMillis,
        @Min(1) int fcmReadTimeoutMillis
) {
}
