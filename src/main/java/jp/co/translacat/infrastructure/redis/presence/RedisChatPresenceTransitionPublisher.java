package jp.co.translacat.infrastructure.redis.presence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.chat.presence.config.ChatPresenceProperties;
import jp.co.translacat.domain.chat.presence.event.ChatPresenceChangedApplicationEvent;
import jp.co.translacat.domain.chat.presence.port.ChatPresenceTransitionPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "translacat.chat.presence",
        name = "enabled",
        havingValue = "true"
)
public class RedisChatPresenceTransitionPublisher
        implements ChatPresenceTransitionPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher localEventPublisher;
    private final RedisChatPresenceSubscriber subscriber;
    private final String eventChannel;

    public RedisChatPresenceTransitionPublisher(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            ApplicationEventPublisher localEventPublisher,
            RedisChatPresenceSubscriber subscriber,
            ChatPresenceProperties properties
    ) {
        properties.validate();
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.localEventPublisher = localEventPublisher;
        this.subscriber = subscriber;
        this.eventChannel = properties.resolveEventChannel();
    }

    @Override
    public void publish(ChatPresenceChangedApplicationEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("presence event must not be null.");
        }

        boolean localFallbackRequired = false;

        try {
            String payload = objectMapper.writeValueAsString(event);
            Long subscriberCount = redisTemplate.convertAndSend(eventChannel, payload);

            // Redis Pub/Sub does not persist messages. If this Backend instance is not
            // currently subscribed (or nobody is subscribed), still fan out locally so
            // an infrastructure outage cannot suppress updates for this instance's users.
            localFallbackRequired = subscriberCount == null
                    || subscriberCount <= 0L
                    || !subscriber.isListening();
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn(
                    "Failed to publish Redis presence event. Falling back to local fan-out. "
                            + "userId={}, online={}, cause={}",
                    event.userId(),
                    event.online(),
                    e.getMessage()
            );
            localFallbackRequired = true;
        }

        if (localFallbackRequired) {
            localEventPublisher.publishEvent(event);
        }
    }
}
