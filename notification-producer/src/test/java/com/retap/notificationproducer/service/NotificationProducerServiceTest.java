package com.retap.notificationproducer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.retap.notificationproducer.config.NotificationProducerProperties;
import com.retap.notificationproducer.domain.NotificationMessage;
import com.retap.notificationproducer.domain.NotificationSendResult;
import com.retap.notificationproducer.domain.UserArrivingGoal;
import com.retap.notificationproducer.repository.UserArrivingGoalRepository;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

class NotificationProducerServiceTest {

    private final UserArrivingGoalRepository repository = org.mockito.Mockito.mock(UserArrivingGoalRepository.class);
    private final KafkaTemplate<Long, NotificationMessage> kafkaTemplate = org.mockito.Mockito.mock(KafkaTemplate.class);
    private final NotificationProducerService service = new NotificationProducerService(
            repository,
            kafkaTemplate,
            new NotificationProducerProperties("push-notifications", 2)
    );

    @Test
    void publishesMessagesWithUserIdAsKafkaKey() {
        when(repository.findArrivingGoalsAfterUserId(0, 2))
                .thenReturn(List.of(
                        new UserArrivingGoal(1, "mock-token-1", "user1", 1),
                        new UserArrivingGoal(2, "mock-token-2", "user2", 3)
                ));
        when(repository.findArrivingGoalsAfterUserId(2, 2)).thenReturn(List.of());
        when(kafkaTemplate.send(eq("push-notifications"), any(Long.class), any(NotificationMessage.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        NotificationSendResult result = service.publishTodayArrivingGoalNotifications(OptionalLong.empty());

        assertThat(result.publishedCount()).isEqualTo(2);
        verify(kafkaTemplate).send(eq("push-notifications"), eq(1L), any(NotificationMessage.class));
        verify(kafkaTemplate).send(eq("push-notifications"), eq(2L), any(NotificationMessage.class));
        verify(kafkaTemplate).flush();
    }

    @Test
    void respectsMaxCountWhenProvided() {
        when(repository.findArrivingGoalsAfterUserId(0, 1))
                .thenReturn(List.of(new UserArrivingGoal(1, "mock-token-1", "user1", 1)));
        when(kafkaTemplate.send(eq("push-notifications"), any(Long.class), any(NotificationMessage.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        NotificationSendResult result = service.publishTodayArrivingGoalNotifications(OptionalLong.of(1));

        assertThat(result.publishedCount()).isEqualTo(1);
        verify(repository).findArrivingGoalsAfterUserId(0, 1);

        ArgumentCaptor<NotificationMessage> messageCaptor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(kafkaTemplate).send(eq("push-notifications"), eq(1L), messageCaptor.capture());
        assertThat(messageCaptor.getValue().title()).contains("user1");
        assertThat(messageCaptor.getValue().body()).contains("1개");
    }
}
