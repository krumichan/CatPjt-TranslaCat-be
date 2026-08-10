package jp.co.translacat.infrastructure.redis.presence;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.chat.presence.config.ChatPresenceProperties;
import jp.co.translacat.domain.chat.presence.event.ChatPresenceChangedApplicationEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@Testcontainers(disabledWithoutDocker = true)
class RedisChatPresencePubSubIntegrationTest {

    private static final int REDIS_PORT = 6379;

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4.10-alpine")
    ).withExposedPorts(REDIS_PORT);

    private LettuceConnectionFactory connectionFactoryA;
    private LettuceConnectionFactory connectionFactoryB;
    private RedisMessageListenerContainer listenerContainerA;
    private RedisMessageListenerContainer listenerContainerB;

    @BeforeEach
    void setUp() {
        connectionFactoryA = createConnectionFactory();
        connectionFactoryB = createConnectionFactory();
    }

    @AfterEach
    void tearDown() {
        stopContainer(listenerContainerA);
        stopContainer(listenerContainerB);
        destroyConnectionFactory(connectionFactoryA);
        destroyConnectionFactory(connectionFactoryB);
    }

    @Test
    void publishedPresenceTransition_IsReceivedByTwoBackendStyleSubscribers() {
        ChatPresenceProperties properties = new ChatPresenceProperties();
        properties.setKeyPrefix("test:chat:presence:pubsub");

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        ApplicationEventPublisher applicationEventPublisherA =
                mock(ApplicationEventPublisher.class);
        ApplicationEventPublisher applicationEventPublisherB =
                mock(ApplicationEventPublisher.class);

        RedisChatPresenceMessageListener messageListenerA =
                new RedisChatPresenceMessageListener(
                        objectMapper,
                        applicationEventPublisherA
                );
        RedisChatPresenceMessageListener messageListenerB =
                new RedisChatPresenceMessageListener(
                        objectMapper,
                        applicationEventPublisherB
                );

        listenerContainerA = createListenerContainer(
                connectionFactoryA,
                messageListenerA,
                properties
        );
        listenerContainerB = createListenerContainer(
                connectionFactoryB,
                messageListenerB,
                properties
        );

        listenerContainerA.start();
        listenerContainerB.start();

        StringRedisTemplate publishingTemplate =
                new StringRedisTemplate(connectionFactoryA);
        publishingTemplate.afterPropertiesSet();

        RedisChatPresenceSubscriber subscriber = mock(RedisChatPresenceSubscriber.class);
        when(subscriber.isListening()).thenReturn(true);
        RedisChatPresenceTransitionPublisher publisher =
                new RedisChatPresenceTransitionPublisher(
                        publishingTemplate,
                        objectMapper,
                        applicationEventPublisherA,
                        subscriber,
                        properties
                );

        ChatPresenceChangedApplicationEvent event =
                new ChatPresenceChangedApplicationEvent(
                        100L,
                        true,
                        LocalDateTime.of(2026, 8, 10, 19, 30)
                );

        publisher.publish(event);

        verify(applicationEventPublisherA, timeout(3_000L))
                .publishEvent(any(ChatPresenceChangedApplicationEvent.class));
        verify(applicationEventPublisherB, timeout(3_000L))
                .publishEvent(any(ChatPresenceChangedApplicationEvent.class));

        assertEquals(
                "test:chat:presence:pubsub:events",
                properties.resolveEventChannel()
        );
    }

    private RedisMessageListenerContainer createListenerContainer(
            LettuceConnectionFactory connectionFactory,
            RedisChatPresenceMessageListener messageListener,
            ChatPresenceProperties properties
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                messageListener,
                new org.springframework.data.redis.listener.ChannelTopic(
                        properties.resolveEventChannel()
                )
        );
        container.afterPropertiesSet();
        return container;
    }

    private LettuceConnectionFactory createConnectionFactory() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(),
                REDIS.getMappedPort(REDIS_PORT)
        );
        connectionFactory.afterPropertiesSet();
        return connectionFactory;
    }

    private void stopContainer(RedisMessageListenerContainer container) {
        if (container == null) {
            return;
        }
        try {
            container.stop();
            container.destroy();
        } catch (Exception ignored) {
            // Test cleanup only.
        }
    }

    private void destroyConnectionFactory(LettuceConnectionFactory connectionFactory) {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }
}
