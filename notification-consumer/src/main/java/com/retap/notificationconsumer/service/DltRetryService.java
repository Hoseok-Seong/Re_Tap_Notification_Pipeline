package com.retap.notificationconsumer.service;

import com.retap.notificationconsumer.client.FcmMockClient;
import com.retap.notificationconsumer.config.NotificationConsumerProperties;
import com.retap.notificationconsumer.domain.FailedNotificationMessage;
import com.retap.notificationconsumer.repository.NotificationFailureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DltRetryService {

    private static final Logger log = LoggerFactory.getLogger(DltRetryService.class);

    private final FcmMockClient fcmMockClient;
    private final NotificationConsumerService consumerService;
    private final NotificationFailureRepository failureRepository;
    private final NotificationConsumerProperties properties;

    public DltRetryService(
            FcmMockClient fcmMockClient,
            NotificationConsumerService consumerService,
            NotificationFailureRepository failureRepository,
            NotificationConsumerProperties properties
    ) {
        this.fcmMockClient = fcmMockClient;
        this.consumerService = consumerService;
        this.failureRepository = failureRepository;
        this.properties = properties;
    }

    public RetryResult retry(FailedNotificationMessage failedMessage) {
        long backoffMillis = backoffMillis(failedMessage.retryCount());
        sleep(backoffMillis);

        try {
            fcmMockClient.send(failedMessage.message());
            log.info("DLT retry succeeded. messageId={}, userId={}, retryCount={}",
                    failedMessage.message().messageId(), failedMessage.message().userId(), failedMessage.retryCount());
            return RetryResult.SUCCESS;
        } catch (Exception e) {
            FailedNotificationMessage nextFailure = failedMessage.nextRetry(e.getMessage());
            if (nextFailure.retryCount() >= properties.maxRetryCount()) {
                failureRepository.save(nextFailure);
                log.warn("DLT retry exhausted. messageId={}, userId={}, retryCount={}",
                        nextFailure.message().messageId(), nextFailure.message().userId(), nextFailure.retryCount());
                return RetryResult.FINAL_FAILURE;
            }

            consumerService.publishToDlt(nextFailure);
            log.info("DLT retry failed. messageId={}, userId={}, nextRetryCount={}",
                    nextFailure.message().messageId(), nextFailure.message().userId(), nextFailure.retryCount());
            return RetryResult.REQUEUED;
        }
    }

    private long backoffMillis(int retryCount) {
        return properties.retryBackoffMillis() * (1L << retryCount);
    }

    private void sleep(long backoffMillis) {
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during DLT retry backoff", e);
        }
    }

    public enum RetryResult {
        SUCCESS,
        REQUEUED,
        FINAL_FAILURE
    }
}
