package com.retap.notificationconsumer.repository;

import com.retap.notificationconsumer.domain.FailedNotificationMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationFailureRepository {

    private final JdbcTemplate jdbcTemplate;

    public NotificationFailureRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(FailedNotificationMessage failedMessage) {
        jdbcTemplate.update("""
                        INSERT INTO notification_failures (
                            message_id,
                            user_id,
                            fcm_token,
                            error_message,
                            retry_count
                        )
                        VALUES (?, ?, ?, ?, ?)
                        """,
                failedMessage.message().messageId(),
                failedMessage.message().userId(),
                failedMessage.message().fcmToken(),
                failedMessage.errorMessage(),
                failedMessage.retryCount()
        );
    }
}
