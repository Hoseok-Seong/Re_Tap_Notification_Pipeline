package com.retap.notificationconsumer.client;

import com.retap.notificationconsumer.config.NotificationConsumerProperties;
import com.retap.notificationconsumer.domain.NotificationMessage;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class FcmMockClient {

    private final RestClient restClient;

    public FcmMockClient(NotificationConsumerProperties properties, RestClient.Builder restClientBuilder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.fcmConnectTimeoutMillis());
        requestFactory.setReadTimeout(properties.fcmReadTimeoutMillis());

        this.restClient = restClientBuilder
                .baseUrl(properties.fcmBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    public void send(NotificationMessage message) {
        restClient.post()
                .uri("/v1/projects/retap-local/messages:send")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "message", Map.of(
                                "token", message.fcmToken(),
                                "notification", Map.of(
                                        "title", message.title(),
                                        "body", message.body()
                                )
                        )
                ))
                .retrieve()
                .toBodilessEntity();
    }
}
