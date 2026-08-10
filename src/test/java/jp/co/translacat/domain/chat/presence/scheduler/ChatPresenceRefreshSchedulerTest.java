package jp.co.translacat.domain.chat.presence.scheduler;

import jp.co.translacat.domain.chat.presence.config.ChatPresenceProperties;
import jp.co.translacat.domain.chat.presence.service.ChatPresenceSessionLifecycleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatPresenceRefreshSchedulerTest {

    @Mock private TaskScheduler taskScheduler;
    @Mock private ChatPresenceSessionLifecycleService lifecycleService;

    @Test
    void start_UsesConfiguredRefreshIntervalAndRefreshNowDelegates() {
        ChatPresenceProperties properties = new ChatPresenceProperties();
        properties.setRefreshInterval(Duration.ofSeconds(20));
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(taskScheduler.scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(20))))
                .thenReturn(future);

        ChatPresenceRefreshScheduler scheduler = new ChatPresenceRefreshScheduler(
                taskScheduler,
                properties,
                lifecycleService
        );

        scheduler.start();
        scheduler.refreshNow();

        verify(taskScheduler).scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(20)));
        verify(lifecycleService).refreshLocalSessions();

        scheduler.stop();
        verify(future).cancel(false);
    }
}
