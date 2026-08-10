package jp.co.translacat.infrastructure.redis.presence;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.chat.presence.config.ChatPresenceProperties;
import jp.co.translacat.domain.chat.presence.event.ChatPresenceChangedApplicationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisChatPresenceTransitionPublisherTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ApplicationEventPublisher localEventPublisher;
    @Mock private RedisChatPresenceSubscriber subscriber;

    @Test
    void publish_WhenRedisFails_FallsBackToLocalApplicationEvent() {
        ChatPresenceProperties properties = new ChatPresenceProperties();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        RedisChatPresenceTransitionPublisher publisher =
                new RedisChatPresenceTransitionPublisher(
                        redisTemplate,
                        objectMapper,
                        localEventPublisher,
                        subscriber,
                        properties
                );
        ChatPresenceChangedApplicationEvent event =
                ChatPresenceChangedApplicationEvent.online(100L);

        doThrow(new IllegalStateException("redis down"))
                .when(redisTemplate)
                .convertAndSend(anyString(), anyString());

        publisher.publish(event);

        verify(localEventPublisher).publishEvent(event);
    }

    @Test
    void publish_WhenLocalSubscriberIsNotListening_FallsBackLocallyEvenIfRedisPublishSucceeds() {
        ChatPresenceProperties properties = new ChatPresenceProperties();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        RedisChatPresenceTransitionPublisher publisher =
                new RedisChatPresenceTransitionPublisher(
                        redisTemplate,
                        objectMapper,
                        localEventPublisher,
                        subscriber,
                        properties
                );
        ChatPresenceChangedApplicationEvent event =
                ChatPresenceChangedApplicationEvent.online(100L);

        when(redisTemplate.convertAndSend(anyString(), anyString())).thenReturn(1L);
        when(subscriber.isListening()).thenReturn(false);

        publisher.publish(event);

        verify(localEventPublisher).publishEvent(event);
    }

    @Test
    void publish_WhenRedisAndLocalSubscriberAreHealthy_DoesNotDuplicateLocalDelivery() {
        ChatPresenceProperties properties = new ChatPresenceProperties();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        RedisChatPresenceTransitionPublisher publisher =
                new RedisChatPresenceTransitionPublisher(
                        redisTemplate,
                        objectMapper,
                        localEventPublisher,
                        subscriber,
                        properties
                );
        ChatPresenceChangedApplicationEvent event =
                ChatPresenceChangedApplicationEvent.online(100L);

        when(redisTemplate.convertAndSend(anyString(), anyString())).thenReturn(2L);
        when(subscriber.isListening()).thenReturn(true);

        publisher.publish(event);

        verify(localEventPublisher, never()).publishEvent(event);
    }

}
