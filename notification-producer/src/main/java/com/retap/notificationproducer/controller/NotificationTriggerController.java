package com.retap.notificationproducer.controller;

import com.retap.notificationproducer.domain.NotificationSendResult;
import com.retap.notificationproducer.service.NotificationProducerService;
import java.util.OptionalLong;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationTriggerController {

    private final NotificationProducerService producerService;

    public NotificationTriggerController(NotificationProducerService producerService) {
        this.producerService = producerService;
    }

    @PostMapping("/send")
    public NotificationSendResult send(@RequestParam(required = false) Long limit) {
        OptionalLong maxCount = limit == null ? OptionalLong.empty() : OptionalLong.of(limit);
        return producerService.publishTodayArrivingGoalNotifications(maxCount);
    }
}
