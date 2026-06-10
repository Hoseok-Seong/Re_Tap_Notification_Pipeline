package com.retap.notificationconsumer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.retap.notificationconsumer.client.FcmMockClient;
import com.retap.notificationconsumer.config.NotificationConsumerProperties;
import com.retap.notificationconsumer.domain.FailedNotificationMessage;
import com.retap.notificationconsumer.domain.NotificationMessage;
import com.retap.notificationconsumer.repository.NotificationFailureRepository;
import org.junit.jupiter.api.Test;

class DltRetryServiceTest {

    private final FcmMockClient fcmMockClient = org.mockito.Mockito.mock(FcmMockClient.class);
    private final NotificationConsumerService consumerService = org.mockito.Mockito.mock(NotificationConsumerService.class);
    private final NotificationFailureRepository failureRepository = org.mockito.Mockito.mock(NotificationFailureRepository.class);
    private final DltRetryService service = new DltRetryService(
            fcmMockClient,
            consumerService,
            failureRepository,
            new NotificationConsumerProperties("push-notifications", "push-notifications-dlt", 3, 1, "http://localhost:8080", 1000, 1000)
    );

    @Test
    void requeuesBeforeMaxRetryCount() {
        FailedNotificationMessage failedMessage = new FailedNotificationMessage(message(), 1, "first failure");
        doThrow(new RuntimeException("second failure")).when(fcmMockClient).send(failedMessage.message());

        DltRetryService.RetryResult result = service.retry(failedMessage);

        assertThat(result).isEqualTo(DltRetryService.RetryResult.REQUEUED);
        verify(consumerService).publishToDlt(new FailedNotificationMessage(message(), 2, "second failure"));
    }

    @Test
    void storesFailureWhenRetryIsExhausted() {
        FailedNotificationMessage failedMessage = new FailedNotificationMessage(message(), 2, "second failure");
        doThrow(new RuntimeException("third failure")).when(fcmMockClient).send(failedMessage.message());

        DltRetryService.RetryResult result = service.retry(failedMessage);

        assertThat(result).isEqualTo(DltRetryService.RetryResult.FINAL_FAILURE);
        verify(failureRepository).save(new FailedNotificationMessage(message(), 3, "third failure"));
    }

    private NotificationMessage message() {
        return new NotificationMessage(
                "message-1",
                1,
                "mock-token-1",
                "title",
                "body",
                "2026-06-11T09:00:00"
        );
    }
}
