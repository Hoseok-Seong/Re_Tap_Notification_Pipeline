package com.retap.notificationconsumer.client;

import com.retap.notificationconsumer.config.NotificationConsumerProperties;
import com.retap.notificationconsumer.domain.NotificationMessage;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.core.ParameterizedTypeReference;
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

    public BatchSendResponse sendBatch(List<NotificationMessage> messages) {
        List<Map<String, Object>> requestMessages = messages.stream()
                .map(message -> Map.<String, Object>of(
                        "token", message.fcmToken(),
                        "notification", Map.of(
                                "title", message.title(),
                                "body", message.body()
                        ),
                        "data", Map.of(
                                "messageId", message.messageId(),
                                "userId", String.valueOf(message.userId())
                        )
                ))
                .toList();

        Map<String, Object> response = restClient.post()
                .uri("/v1/projects/retap-local/messages:sendBatch")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("messages", requestMessages))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        return BatchSendResponse.from(response);
    }

    public record BatchSendResponse(long successCount, long failureCount, List<SendResponse> responses) {

        public static BatchSendResponse from(Map<String, Object> response) {
            long successCount = numberValue(response.get("successCount"));
            long failureCount = numberValue(response.get("failureCount"));
            Object rawResponses = response.get("responses");
            if (!(rawResponses instanceof List<?> responseItems)) {
                throw new IllegalStateException("FCM batch response does not contain responses");
            }

            List<SendResponse> responses = responseItems.stream()
                    .map(SendResponse::from)
                    .toList();

            return new BatchSendResponse(successCount, failureCount, responses);
        }

        private static long numberValue(Object value) {
            if (value instanceof Number number) {
                return number.longValue();
            }
            throw new IllegalStateException("FCM batch response count is not numeric");
        }
    }

    public record SendResponse(boolean success, String messageId, String errorMessage) {

        public static SendResponse from(Object value) {
            if (!(value instanceof Map<?, ?> map)) {
                throw new IllegalStateException("FCM batch response item is not an object");
            }

            boolean success = Boolean.TRUE.equals(map.get("success"));
            if (success) {
                return new SendResponse(true, (String) map.get("messageId"), null);
            }

            Object error = map.get("error");
            String errorMessage = "unknown-fcm-batch-error";
            if (error instanceof Map<?, ?> errorMap && errorMap.get("message") instanceof String message) {
                errorMessage = message;
            }
            return new SendResponse(false, null, errorMessage);
        }
    }
}
