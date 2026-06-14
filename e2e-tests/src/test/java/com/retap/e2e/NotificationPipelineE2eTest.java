package com.retap.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NotificationPipelineE2eTest {

    private static final HttpJsonClient http = new HttpJsonClient();
    private static final DatabaseSupport database = new DatabaseSupport();
    private static final KafkaSupport kafka = new KafkaSupport();

    @BeforeAll
    static void verifyServicesAreAvailable() throws Exception {
        http.get(E2eConfig.PRODUCER_BASE_URL + "/status", Object.class);
        http.get(E2eConfig.CONSUMER_BASE_URL + "/status", Object.class);
        http.get(E2eConfig.FCM_MOCK_BASE_URL + "/metrics", MetricsResponse.class);
    }

    @Test
    void producerToKafkaToConsumerToFcmMock() throws Exception {
        database.prepareArrivalsForToday(20);
        MetricsResponse before = http.get(E2eConfig.FCM_MOCK_BASE_URL + "/metrics", MetricsResponse.class);

        ProducerResponse response = http.post(
                E2eConfig.PRODUCER_BASE_URL + "/api/notifications/send?limit=3",
                ProducerResponse.class
        );

        assertThat(response.publishedCount()).isEqualTo(3);
        Eventually.untilAsserted(Duration.ofSeconds(10), () -> {
            MetricsResponse after = http.get(E2eConfig.FCM_MOCK_BASE_URL + "/metrics", MetricsResponse.class);
            assertThat(after.totalRequests()).isGreaterThanOrEqualTo(before.totalRequests() + 3);
        });
    }

    @Test
    void dltRetryConsumerSendsFailedMessageToFcmMock() throws Exception {
        MetricsResponse before = http.get(E2eConfig.FCM_MOCK_BASE_URL + "/metrics", MetricsResponse.class);

        kafka.publishFailedNotificationToDlt(99_001);

        Eventually.untilAsserted(Duration.ofSeconds(10), () -> {
            MetricsResponse after = http.get(E2eConfig.FCM_MOCK_BASE_URL + "/metrics", MetricsResponse.class);
            assertThat(after.totalRequests()).isGreaterThanOrEqualTo(before.totalRequests() + 1);
        });
    }
}
