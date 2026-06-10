package com.retap.notificationconsumer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retap.notificationconsumer.domain.FailedNotificationMessage;
import com.retap.notificationconsumer.domain.NotificationMessage;
import org.springframework.stereotype.Component;

@Component
public class NotificationMessageParser {

    private final ObjectMapper objectMapper;

    public NotificationMessageParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public NotificationMessage parseNotification(String payload) {
        try {
            return objectMapper.readValue(payload, NotificationMessage.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid notification payload", e);
        }
    }

    public FailedNotificationMessage parseFailedNotification(String payload) {
        try {
            return objectMapper.readValue(payload, FailedNotificationMessage.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid failed notification payload", e);
        }
    }
}
