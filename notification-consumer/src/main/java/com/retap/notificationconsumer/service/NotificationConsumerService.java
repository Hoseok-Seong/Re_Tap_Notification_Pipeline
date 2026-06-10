package com.retap.notificationconsumer.service;

import com.retap.notificationconsumer.client.FcmMockClient;
import com.retap.notificationconsumer.config.NotificationConsumerProperties;
import com.retap.notificationconsumer.domain.FailedNotificationMessage;
import com.retap.notificationconsumer.domain.NotificationMessage;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationConsumerService {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumerService.class);

    private final FcmMockClient fcmMockClient;
    private final KafkaTemplate<Long, FailedNotificationMessage> kafkaTemplate;
    private final NotificationConsumerProperties properties;

    public NotificationConsumerService(
            FcmMockClient fcmMockClient,
            KafkaTemplate<Long, FailedNotificationMessage> kafkaTemplate,
            NotificationConsumerProperties properties
    ) {
        this.fcmMockClient = fcmMockClient;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    public BatchResult sendBatch(List<NotificationMessage> messages) {
        long startedAt = System.nanoTime();
        long successCount = 0;
        long failureCount = 0;

        log.info("Notification consume batch started. batchCount={}", messages.size());

        for (NotificationMessage message : messages) {
            try {
                fcmMockClient.send(message);
                successCount++;
            } catch (Exception e) {
                failureCount++;
                publishToDlt(new FailedNotificationMessage(message, 0, e.getMessage()));
            }
        }

        kafkaTemplate.flush();

        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
        double throughput = messages.isEmpty() || elapsedMillis == 0
                ? 0.0
                : messages.size() / (elapsedMillis / 1000.0);

        log.info("Notification consume batch completed. batchCount={}, successCount={}, failureCount={}, elapsedMillis={}, throughput={} msg/s",
                messages.size(), successCount, failureCount, elapsedMillis, String.format("%.2f", throughput));

        return new BatchResult(messages.size(), successCount, failureCount, elapsedMillis, throughput);
    }

    public void publishToDlt(FailedNotificationMessage failedMessage) {
        kafkaTemplate.send(properties.dltTopic(), failedMessage.message().userId(), failedMessage);
    }

    public record BatchResult(
            long batchCount,
            long successCount,
            long failureCount,
            long elapsedMillis,
            double throughputPerSecond
    ) {
    }
}
