package jp.co.translacat.infrastructure.redis.presence;

import jp.co.translacat.domain.chat.presence.config.ChatPresenceProperties;
import jp.co.translacat.domain.chat.presence.port.ChatPresenceStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class RedisChatPresenceStore implements ChatPresenceStore {

    private static final DefaultRedisScript<Long> REGISTER_SCRIPT = new DefaultRedisScript<>("""
            redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', ARGV[5])
            redis.call('PSETEX', KEYS[1], ARGV[3], ARGV[1])
            redis.call('ZADD', KEYS[2], ARGV[4], ARGV[2])
            redis.call('PEXPIRE', KEYS[2], ARGV[3])
            return redis.call('ZCARD', KEYS[2])
            """, Long.class);

    private static final DefaultRedisScript<Long> REFRESH_SCRIPT = new DefaultRedisScript<>("""
            local owner = redis.call('GET', KEYS[1])
            if not owner then
                return 0
            end
            redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', ARGV[5])
            redis.call('PSETEX', KEYS[1], ARGV[3], ARGV[1])
            redis.call('ZADD', KEYS[2], ARGV[4], ARGV[2])
            redis.call('PEXPIRE', KEYS[2], ARGV[3])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> REMOVE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('DEL', KEYS[1])
            redis.call('ZREM', KEYS[2], ARGV[1])
            redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', ARGV[2])
            local count = redis.call('ZCARD', KEYS[2])
            if count == 0 then
                redis.call('DEL', KEYS[2])
            end
            return count
            """, Long.class);

    private static final DefaultRedisScript<Long> COUNT_SCRIPT = new DefaultRedisScript<>("""
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
            local count = redis.call('ZCARD', KEYS[1])
            if count == 0 then
                redis.call('DEL', KEYS[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ChatPresenceProperties properties;

    public RedisChatPresenceStore(
            StringRedisTemplate redisTemplate,
            ChatPresenceProperties properties
    ) {
        properties.validate();
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public long registerSession(Long userId, String sessionId) {
        validateArguments(userId, sessionId);

        long now = System.currentTimeMillis();
        long ttlMillis = properties.getSessionTtl().toMillis();
        Long result = redisTemplate.execute(
                REGISTER_SCRIPT,
                List.of(sessionKey(userId, sessionId), userSessionsKey(userId)),
                userId.toString(),
                sessionId,
                Long.toString(ttlMillis),
                Long.toString(now + ttlMillis),
                Long.toString(now)
        );

        long activeSessionCount = requireScriptResult(result, "register session");
        return activeSessionCount;
    }

    @Override
    public boolean refreshSession(Long userId, String sessionId) {
        validateArguments(userId, sessionId);

        long now = System.currentTimeMillis();
        long ttlMillis = properties.getSessionTtl().toMillis();
        Long result = redisTemplate.execute(
                REFRESH_SCRIPT,
                List.of(sessionKey(userId, sessionId), userSessionsKey(userId)),
                userId.toString(),
                sessionId,
                Long.toString(ttlMillis),
                Long.toString(now + ttlMillis),
                Long.toString(now)
        );

        long refreshed = requireScriptResult(result, "refresh session");
        return refreshed == 1;
    }

    @Override
    public long removeSession(Long userId, String sessionId) {
        validateArguments(userId, sessionId);

        long now = System.currentTimeMillis();
        Long result = redisTemplate.execute(
                REMOVE_SCRIPT,
                List.of(sessionKey(userId, sessionId), userSessionsKey(userId)),
                sessionId,
                Long.toString(now)
        );

        long activeSessionCount = requireScriptResult(result, "remove session");
        return activeSessionCount;
    }

    @Override
    public long getActiveSessionCount(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null.");
        }

        Long result = redisTemplate.execute(
                COUNT_SCRIPT,
                List.of(userSessionsKey(userId)),
                Long.toString(System.currentTimeMillis())
        );
        return requireScriptResult(result, "count active sessions");
    }

    private long requireScriptResult(Long result, String operation) {
        if (result == null) {
            throw new IllegalStateException("Redis presence script returned null while attempting to " + operation + ".");
        }
        return result;
    }

    private void validateArguments(Long userId, String sessionId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null.");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank.");
        }
    }

    private String sessionKey(Long userId, String sessionId) {
        return userKeyPrefix(userId) + ":session:" + sessionId;
    }

    private String userSessionsKey(Long userId) {
        return userKeyPrefix(userId) + ":sessions";
    }

    private String userKeyPrefix(Long userId) {
        // Redis Cluster에서도 같은 User의 Session Key와 Index가 같은 hash slot을 사용하도록 한다.
        return properties.getKeyPrefix() + ":user:{" + userId + "}";
    }
}
