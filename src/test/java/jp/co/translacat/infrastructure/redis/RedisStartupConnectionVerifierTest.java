package jp.co.translacat.infrastructure.redis;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisStartupConnectionVerifierTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final RedisStartupConnectionVerifier verifier =
            new RedisStartupConnectionVerifier(redisTemplate);

    @Test
    @SuppressWarnings("unchecked")
    void run_WhenRedisReturnsPong_CompletesNormally() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn("PONG");

        assertDoesNotThrow(() -> verifier.run(mock(ApplicationArguments.class)));

        verify(redisTemplate).execute(any(RedisCallback.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void run_WhenRedisConnectionFails_DoesNotFailApplicationStartup() {
        when(redisTemplate.execute(any(RedisCallback.class)))
                .thenThrow(new IllegalStateException("connection refused"));

        assertDoesNotThrow(() -> verifier.run(mock(ApplicationArguments.class)));

        verify(redisTemplate).execute(any(RedisCallback.class));
    }
}
