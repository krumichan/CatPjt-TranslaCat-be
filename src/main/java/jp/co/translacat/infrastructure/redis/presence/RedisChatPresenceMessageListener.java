package jp.co.translacat.infrastructure.redis.presence;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.chat.presence.event.ChatPresenceChangedApplicationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "translacat.chat.presence",
        name = "enabled",
        havingValue = "true"
)
public class RedisChatPresenceMessageListener implements MessageListener {

    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (message == null || message.getBody() == null) {
            return;
        }

        try {
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            ChatPresenceChangedApplicationEvent event = objectMapper.readValue(
                    payload,
                    ChatPresenceChangedApplicationEvent.class
            );
            applicationEventPublisher.publishEvent(event);
        } catch (Exception e) {
            // Invalid/legacy Pub/Sub payload must not terminate the listener container.
            log.warn(
                    "Failed to consume Redis presence event. payloadBytes={}, cause={}",
                    message.getBody().length,
                    e.getMessage()
            );
        }
    }
}
