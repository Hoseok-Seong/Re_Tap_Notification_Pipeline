package com.retap.notificationconsumer.service;

import com.retap.notificationconsumer.domain.FailedNotificationMessage;
import com.retap.notificationconsumer.domain.NotificationMessage;
import java.util.List;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaNotificationListener {

    private final NotificationMessageParser parser;
    private final NotificationConsumerService consumerService;
    private final DltRetryService dltRetryService;

    public KafkaNotificationListener(
            NotificationMessageParser parser,
            NotificationConsumerService consumerService,
            DltRetryService dltRetryService
    ) {
        this.parser = parser;
        this.consumerService = consumerService;
        this.dltRetryService = dltRetryService;
    }

    @KafkaListener(
            topics = "${notification.consumer.topic}",
            containerFactory = "batchKafkaListenerContainerFactory"
    )
    public void consumeNotifications(List<String> payloads) {
        List<NotificationMessage> messages = payloads.stream()
                .map(parser::parseNotification)
                .toList();
        consumerService.sendBatch(messages);
    }

    @KafkaListener(
            topics = "${notification.consumer.dlt-topic}",
            groupId = "${spring.kafka.consumer.group-id}-dlt",
            containerFactory = "batchKafkaListenerContainerFactory"
    )
    public void consumeDlt(List<String> payloads) {
        List<FailedNotificationMessage> failedMessages = payloads.stream()
                .map(parser::parseFailedNotification)
                .toList();
        failedMessages.forEach(dltRetryService::retry);
    }
}
