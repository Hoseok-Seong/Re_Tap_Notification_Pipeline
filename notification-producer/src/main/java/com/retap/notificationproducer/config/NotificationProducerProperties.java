package com.retap.notificationproducer.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "notification.producer")
public record NotificationProducerProperties(
        @NotBlank String topic,
        @Min(1) int batchSize
) {
}
