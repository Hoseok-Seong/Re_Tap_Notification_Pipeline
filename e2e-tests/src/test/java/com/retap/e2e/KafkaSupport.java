package com.retap.e2e;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.LongSerializer;
import org.apache.kafka.common.serialization.StringSerializer;

final class KafkaSupport {

    void publishFailedNotificationToDlt(long userId) throws Exception {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", E2eConfig.KAFKA_BOOTSTRAP_SERVERS);
        properties.put("key.serializer", LongSerializer.class.getName());
        properties.put("value.serializer", StringSerializer.class.getName());

        String messageId = UUID.randomUUID().toString();
        String payload = """
                {
                  "message": {
                    "messageId": "%s",
                    "userId": %d,
                    "fcmToken": "mock-token-%d",
                    "title": "E2E retry title",
                    "body": "E2E retry body",
                    "createdAt": "%s"
                  },
                  "retryCount": 0,
                  "errorMessage": "e2e injected failure"
                }
                """.formatted(
                messageId,
                userId,
                userId,
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );

        try (KafkaProducer<Long, String> producer = new KafkaProducer<>(properties)) {
            producer.send(new ProducerRecord<>("push-notifications-dlt", userId, payload)).get();
            producer.flush();
        }
    }
}
