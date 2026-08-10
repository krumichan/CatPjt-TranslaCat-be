package jp.co.translacat.infrastructure.redis.presence;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import jp.co.translacat.domain.chat.presence.config.ChatPresenceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "translacat.chat.presence",
        name = "enabled",
        havingValue = "true"
)
public class RedisChatPresenceSubscriber {

    private static final Duration START_RETRY_DELAY = Duration.ofSeconds(5);

    private final RedisMessageListenerContainer listenerContainer;
    private final TaskScheduler taskScheduler;
    private final AtomicBoolean retryScheduled = new AtomicBoolean(false);

    private volatile boolean stopping;

    @Autowired
    public RedisChatPresenceSubscriber(
            RedisConnectionFactory connectionFactory,
            RedisChatPresenceMessageListener messageListener,
            ChatPresenceProperties properties,
            @Qualifier("chatPresenceTaskScheduler") TaskScheduler taskScheduler
    ) {
        properties.validate();
        this.taskScheduler = taskScheduler;
        this.listenerContainer = createListenerContainer(
                connectionFactory,
                messageListener,
                properties
        );
    }

    RedisChatPresenceSubscriber(
            RedisMessageListenerContainer listenerContainer,
            TaskScheduler taskScheduler
    ) {
        this.listenerContainer = listenerContainer;
        this.taskScheduler = taskScheduler;
    }

    private RedisMessageListenerContainer createListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisChatPresenceMessageListener messageListener,
            ChatPresenceProperties properties
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setRecoveryInterval(5_000L);
        container.setErrorHandler(error -> log.warn(
                "Redis presence Pub/Sub listener error. Presence events may be temporarily delayed/lost. cause={}",
                error.getMessage()
        ));
        container.addMessageListener(
                messageListener,
                new ChannelTopic(properties.resolveEventChannel())
        );
        container.afterPropertiesSet();
        return container;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startAfterApplicationReady() {
        startSafely();
    }

    public boolean isListening() {
        return listenerContainer.isListening();
    }

    void startSafely() {
        if (stopping || listenerContainer.isListening()) {
            return;
        }

        try {
            listenerContainer.start();
            retryScheduled.set(false);
            log.info("Redis presence Pub/Sub subscriber started.");
        } catch (RuntimeException e) {
            // Initial Redis subscription can fail while the Backend itself is otherwise healthy.
            // Reset the container before the next attempt and never propagate this into app startup.
            stopContainerSafely();
            log.warn(
                    "Redis presence Pub/Sub subscriber start failed. "
                            + "Backend remains available and subscriber start will be retried. cause={}",
                    e.getMessage()
            );
            scheduleRetry();
        }
    }

    private void scheduleRetry() {
        if (stopping || !retryScheduled.compareAndSet(false, true)) {
            return;
        }

        try {
            taskScheduler.schedule(() -> {
                retryScheduled.set(false);
                startSafely();
            }, Instant.now().plus(START_RETRY_DELAY));
        } catch (RuntimeException e) {
            retryScheduled.set(false);
            log.warn(
                    "Failed to schedule Redis presence Pub/Sub subscriber retry. cause={}",
                    e.getMessage()
            );
        }
    }

    private void stopContainerSafely() {
        try {
            if (listenerContainer.isRunning()) {
                listenerContainer.stop();
            }
        } catch (RuntimeException stopError) {
            log.warn(
                    "Failed to reset Redis presence Pub/Sub subscriber. cause={}",
                    stopError.getMessage()
            );
        }
    }

    @PreDestroy
    public void stop() {
        stopping = true;
        retryScheduled.set(false);
        stopContainerSafely();
        try {
            listenerContainer.destroy();
        } catch (Exception destroyError) {
            log.warn(
                    "Failed to destroy Redis presence Pub/Sub subscriber. cause={}",
                    destroyError.getMessage()
            );
        }
    }
}
