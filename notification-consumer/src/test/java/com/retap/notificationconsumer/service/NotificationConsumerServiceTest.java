package com.retap.notificationconsumer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.retap.notificationconsumer.client.FcmMockClient;
import com.retap.notificationconsumer.config.NotificationConsumerProperties;
import com.retap.notificationconsumer.domain.FailedNotificationMessage;
import com.retap.notificationconsumer.domain.NotificationMessage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

class NotificationConsumerServiceTest {

    private final FcmMockClient fcmMockClient = org.mockito.Mockito.mock(FcmMockClient.class);
    private final KafkaTemplate<Long, FailedNotificationMessage> kafkaTemplate = org.mockito.Mockito.mock(KafkaTemplate.class);
    private final NotificationConsumerService service = new NotificationConsumerService(
            fcmMockClient,
            kafkaTemplate,
            new NotificationConsumerProperties("push-notifications", "push-notifications-dlt", 3, 1, "http://localhost:8080", 1000, 1000)
    );

    @Test
    void sendsBatchAndPublishesFailuresToDlt() {
        NotificationMessage success = message(1);
        NotificationMessage failure = message(2);
        when(fcmMockClient.sendBatch(List.of(success, failure)))
                .thenReturn(new FcmMockClient.BatchSendResponse(
                        1,
                        1,
                        List.of(
                                new FcmMockClient.SendResponse(true, "fcm-message-1", null),
                                new FcmMockClient.SendResponse(false, null, "fcm failed")
                        )
                ));

        NotificationConsumerService.BatchResult result = service.sendBatch(List.of(success, failure));

        assertThat(result.batchCount()).isEqualTo(2);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failureCount()).isEqualTo(1);

        ArgumentCaptor<FailedNotificationMessage> captor = ArgumentCaptor.forClass(FailedNotificationMessage.class);
        verify(kafkaTemplate).send(eq("push-notifications-dlt"), eq(2L), captor.capture());
        assertThat(captor.getValue().retryCount()).isZero();
        assertThat(captor.getValue().message().userId()).isEqualTo(2);
    }

    private NotificationMessage message(long userId) {
        return new NotificationMessage(
                "message-" + userId,
                userId,
                "mock-token-" + userId,
                "title",
                "body",
                "2026-06-11T09:00:00"
        );
    }
}
