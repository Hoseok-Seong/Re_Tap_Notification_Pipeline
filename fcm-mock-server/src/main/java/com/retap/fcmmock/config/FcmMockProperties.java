package com.retap.fcmmock.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "fcm-mock")
public record FcmMockProperties(
        @Min(0) long responseDelayMs,
        @DecimalMin("0.0") @DecimalMax("100.0") double failureRatePercent
) {
}
