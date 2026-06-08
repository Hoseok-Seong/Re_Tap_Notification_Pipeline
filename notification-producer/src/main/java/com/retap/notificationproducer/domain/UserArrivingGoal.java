package com.retap.notificationproducer.domain;

public record UserArrivingGoal(
        long userId,
        String fcmToken,
        String nickname,
        long count
) {
}
