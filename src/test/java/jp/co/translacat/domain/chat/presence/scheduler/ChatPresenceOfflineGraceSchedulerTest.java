package jp.co.translacat.domain.chat.presence.scheduler;

import jp.co.translacat.domain.chat.presence.config.ChatPresenceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatPresenceOfflineGraceSchedulerTest {

    @Mock private TaskScheduler taskScheduler;

    private ChatPresenceOfflineGraceScheduler scheduler;

    @BeforeEach
    void setUp() {
        ChatPresenceProperties properties = new ChatPresenceProperties();
        properties.setOfflineGrace(Duration.ofSeconds(30));
        scheduler = new ChatPresenceOfflineGraceScheduler(taskScheduler, properties);
    }

    @Test
    void cancel_InvalidatesAlreadyScheduledOfflineTask() {
        AtomicInteger executions = new AtomicInteger();
        scheduler.schedule(100L, executions::incrementAndGet);

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(runnableCaptor.capture(), any(Instant.class));
        assertTrue(scheduler.isPending(100L));

        assertTrue(scheduler.cancel(100L));
        assertFalse(scheduler.isPending(100L));

        runnableCaptor.getValue().run();
        assertTrue(executions.get() == 0);
    }
}
