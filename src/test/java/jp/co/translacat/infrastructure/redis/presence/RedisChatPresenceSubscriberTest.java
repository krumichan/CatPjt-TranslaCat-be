package jp.co.translacat.infrastructure.redis.presence;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.TaskScheduler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisChatPresenceSubscriberTest {

    @Mock private RedisMessageListenerContainer listenerContainer;
    @Mock private TaskScheduler taskScheduler;

    @Test
    void startAfterApplicationReady_WhenRedisIsDown_DoesNotFailAndRetries() {
        RedisChatPresenceSubscriber subscriber =
                new RedisChatPresenceSubscriber(
                        listenerContainer,
                        taskScheduler
                );

        when(listenerContainer.isListening()).thenReturn(false);
        when(listenerContainer.isRunning()).thenReturn(true);
        doThrow(new IllegalStateException("redis down"))
                .doNothing()
                .when(listenerContainer)
                .start();

        assertDoesNotThrow(subscriber::startAfterApplicationReady);

        verify(listenerContainer).stop();

        ArgumentCaptor<Runnable> retryTask = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(retryTask.capture(), any(Instant.class));

        retryTask.getValue().run();

        verify(listenerContainer, times(2)).start();
    }
}
