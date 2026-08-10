package jp.co.translacat.domain.chat.presence.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jakarta.annotation.PreDestroy;
import jp.co.translacat.domain.chat.presence.config.ChatPresenceProperties;
import jp.co.translacat.domain.chat.presence.service.ChatPresenceSessionLifecycleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledFuture;

@Slf4j
@ConditionalOnProperty(
        prefix = "translacat.chat.presence",
        name = "enabled",
        havingValue = "true"
)
@Component
public class ChatPresenceRefreshScheduler {

    private final TaskScheduler taskScheduler;
    private final ChatPresenceProperties properties;
    private final ChatPresenceSessionLifecycleService lifecycleService;

    private ScheduledFuture<?> scheduledFuture;

    public ChatPresenceRefreshScheduler(
            @Qualifier("chatPresenceTaskScheduler") TaskScheduler taskScheduler,
            ChatPresenceProperties properties,
            ChatPresenceSessionLifecycleService lifecycleService
    ) {
        properties.validate();
        this.taskScheduler = taskScheduler;
        this.properties = properties;
        this.lifecycleService = lifecycleService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() {
        if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
            return;
        }

        scheduledFuture = taskScheduler.scheduleWithFixedDelay(
                this::refreshSafely,
                properties.getRefreshInterval()
        );

        log.info(
                "Chat presence refresh scheduler started. interval={}ms",
                properties.getRefreshInterval().toMillis()
        );
    }

    public void refreshNow() {
        lifecycleService.refreshLocalSessions();
    }

    private void refreshSafely() {
        try {
            refreshNow();
        } catch (RuntimeException e) {
            // Individual Redis operations are already isolated, but keep the scheduler alive
            // even if an unexpected programming/runtime error escapes the lifecycle service.
            log.error("Unexpected chat presence refresh scheduler failure.", e);
        }
    }

    @PreDestroy
    public synchronized void stop() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            scheduledFuture = null;
        }
    }
}
