package jp.co.translacat.infrastructure.redis.presence;

import jp.co.translacat.domain.chat.presence.config.ChatPresenceProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class RedisChatPresenceStoreIntegrationTest {

    private static final int REDIS_PORT = 6379;

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4.10-alpine")
    ).withExposedPorts(REDIS_PORT);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;

    private ChatPresenceProperties properties;
    private RedisChatPresenceStore store;

    @BeforeAll
    static void setUpRedisClient() {
        connectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(),
                REDIS.getMappedPort(REDIS_PORT)
        );
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void closeRedisClient() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @BeforeEach
    void setUp() {
        RedisCallback<String> flushCallback = connection -> {
            connection.serverCommands().flushAll();
            return "OK";
        };
        redisTemplate.execute(flushCallback);

        properties = new ChatPresenceProperties();
        properties.setSessionTtl(Duration.ofMillis(900));
        properties.setRefreshInterval(Duration.ofMillis(250));
        properties.setOfflineGrace(Duration.ofMillis(300));
        properties.setKeyPrefix("test:chat:presence");

        store = new RedisChatPresenceStore(redisTemplate, properties);
    }

    @Test
    void registerSession_StoresNativeTtlAndEventuallyExpires() {
        long count = store.registerSession(100L, "session-a");

        assertEquals(1L, count);
        assertTrue(store.isOnline(100L));

        Long ttlMillis = redisTemplate.getExpire(
                "test:chat:presence:user:{100}:session:session-a",
                TimeUnit.MILLISECONDS
        );
        assertTrue(ttlMillis != null && ttlMillis > 0 && ttlMillis <= 900);

        await(Duration.ofSeconds(3), () -> !store.isOnline(100L));
        assertFalse(Boolean.TRUE.equals(
                redisTemplate.hasKey("test:chat:presence:user:{100}:session:session-a")
        ));
    }

    @Test
    void refreshSession_ExtendsOnlyThatSessionWhileExpiredSiblingIsIgnored() throws InterruptedException {
        store.registerSession(200L, "session-a");
        store.registerSession(200L, "session-b");
        assertEquals(2L, store.getActiveSessionCount(200L));

        Thread.sleep(550L);
        assertTrue(store.refreshSession(200L, "session-b"));

        await(
                Duration.ofSeconds(2),
                () -> !Boolean.TRUE.equals(
                        redisTemplate.hasKey("test:chat:presence:user:{200}:session:session-a")
                )
        );

        assertEquals(1L, store.getActiveSessionCount(200L));
        assertTrue(store.isOnline(200L));

        assertEquals(0L, store.removeSession(200L, "session-b"));
        assertFalse(store.isOnline(200L));
    }

    @Test
    void refreshSession_WhenSessionAlreadyExpired_DoesNotResurrectIt() {
        store.registerSession(300L, "session-a");

        await(
                Duration.ofSeconds(3),
                () -> !Boolean.TRUE.equals(
                        redisTemplate.hasKey("test:chat:presence:user:{300}:session:session-a")
                )
        );

        assertFalse(store.refreshSession(300L, "session-a"));
        assertEquals(0L, store.getActiveSessionCount(300L));
    }

    @Test
    void registerSession_WhenSameSessionIsRegisteredAgain_RemainsIdempotent() {
        assertEquals(1L, store.registerSession(400L, "same-session"));
        assertEquals(1L, store.registerSession(400L, "same-session"));
        assertEquals(1L, store.getActiveSessionCount(400L));
    }

    @Test
    void removeSession_RemovesOnlyRequestedSession() {
        store.registerSession(500L, "session-a");
        store.registerSession(500L, "session-b");

        assertEquals(1L, store.removeSession(500L, "session-a"));
        assertTrue(store.isOnline(500L));
        assertEquals(1L, store.getActiveSessionCount(500L));
    }

    private void await(Duration timeout, BooleanSupplier condition) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for Redis TTL condition.", e);
            }
        }
        throw new AssertionError("Condition was not satisfied within " + timeout + ".");
    }
}
