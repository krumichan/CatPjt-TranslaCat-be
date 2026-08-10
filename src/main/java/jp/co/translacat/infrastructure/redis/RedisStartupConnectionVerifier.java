package jp.co.translacat.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnectionCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "translacat.redis",
        name = "verify-on-startup",
        havingValue = "true"
)
public class RedisStartupConnectionVerifier implements ApplicationRunner {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            RedisCallback<String> pingCallback = RedisConnectionCommands::ping;
            String pong = redisTemplate.execute(pingCallback);

            if ("PONG".equalsIgnoreCase(pong)) {
                log.info("Redis startup connection verification succeeded.");
                return;
            }

            log.warn("Redis startup connection verification returned unexpected response: {}", pong);
        } catch (RuntimeException e) {
            // Presence는 Chat Core Flow보다 낮은 우선순위의 보조 기능이다.
            // Redis 장애만으로 Backend 기동을 실패시키지 않고 degraded 상태로 계속 실행한다.
            log.warn(
                    "Redis startup connection verification failed. "
                            + "Backend will continue without Redis-dependent presence features. cause={}",
                    e.getMessage()
            );
        }
    }
}
