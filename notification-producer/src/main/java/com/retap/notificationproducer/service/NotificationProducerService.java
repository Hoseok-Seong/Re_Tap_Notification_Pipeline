package com.retap.notificationproducer.service;

import com.retap.notificationproducer.config.NotificationProducerProperties;
import com.retap.notificationproducer.domain.NotificationMessage;
import com.retap.notificationproducer.domain.NotificationSendResult;
import com.retap.notificationproducer.domain.UserArrivingGoal;
import com.retap.notificationproducer.repository.UserArrivingGoalRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationProducerService {

    private static final Logger log = LoggerFactory.getLogger(NotificationProducerService.class);

    private final UserArrivingGoalRepository repository;
    private final KafkaTemplate<Long, NotificationMessage> kafkaTemplate;
    private final NotificationProducerProperties properties;

    public NotificationProducerService(
            UserArrivingGoalRepository repository,
            KafkaTemplate<Long, NotificationMessage> kafkaTemplate,
            NotificationProducerProperties properties
    ) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    public NotificationSendResult publishTodayArrivingGoalNotifications(OptionalLong maxCount) {
        long startedAt = System.nanoTime();
        long publishedCount = 0;
        long lastUserId = 0;

        log.info("Notification publish started. topic={}, batchSize={}, maxCount={}",
                properties.topic(), properties.batchSize(), maxCount.isPresent() ? maxCount.getAsLong() : "unlimited");

        while (shouldContinue(publishedCount, maxCount)) {
            int currentBatchSize = currentBatchSize(publishedCount, maxCount);
            List<UserArrivingGoal> users = repository.findArrivingGoalsAfterUserId(lastUserId, currentBatchSize);
            if (users.isEmpty()) {
                break;
            }

            for (UserArrivingGoal user : users) {
                NotificationMessage message = toMessage(user);
                kafkaTemplate.send(properties.topic(), user.userId(), message);
            }

            publishedCount += users.size();
            lastUserId = users.get(users.size() - 1).userId();
            log.info("Notification publish batch completed. lastUserId={}, batchCount={}, totalPublished={}",
                    lastUserId, users.size(), publishedCount);
        }

        kafkaTemplate.flush();

        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;
        double throughput = publishedCount == 0 || elapsedMillis == 0
                ? 0.0
                : publishedCount / (elapsedMillis / 1000.0);

        log.info("Notification publish completed. publishedCount={}, elapsedMillis={}, throughput={} msg/s",
                publishedCount, elapsedMillis, String.format("%.2f", throughput));

        return new NotificationSendResult(publishedCount, elapsedMillis, throughput);
    }

    private boolean shouldContinue(long publishedCount, OptionalLong maxCount) {
        return maxCount.isEmpty() || publishedCount < maxCount.getAsLong();
    }

    private int currentBatchSize(long publishedCount, OptionalLong maxCount) {
        if (maxCount.isEmpty()) {
            return properties.batchSize();
        }

        long remaining = maxCount.getAsLong() - publishedCount;
        return (int) Math.min(properties.batchSize(), remaining);
    }

    private NotificationMessage toMessage(UserArrivingGoal user) {
        return new NotificationMessage(
                UUID.randomUUID().toString(),
                user.userId(),
                user.fcmToken(),
                "🎯 " + user.nickname() + "님, 오늘 도착한 목표!",
                "오늘 도착한 목표가 " + user.count() + "개 있어요.",
                LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }
}
