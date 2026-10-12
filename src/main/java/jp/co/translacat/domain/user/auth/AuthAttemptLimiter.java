package jp.co.translacat.domain.user.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** 단일 BE 인스턴스 안에서 비밀번호 검사 횟수를 제한한다. 비밀번호 원문은 받거나 저장하지 않는다. */
@Component
public class AuthAttemptLimiter {
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(1);
    private static final int MAX_ATTEMPTS = 10;
    private static final int DEFAULT_MAX_KEYS = 10_000;

    private final Map<AttemptKey, ArrayDeque<Instant>> attempts = new HashMap<>();
    private final Clock clock;
    private final int maximumKeys;
    private Instant nextCleanup = Instant.MIN;
    private Instant latestObserved = Instant.MIN;

    public AuthAttemptLimiter() {
        this(Clock.systemUTC(), DEFAULT_MAX_KEYS);
    }

    AuthAttemptLimiter(Clock clock, int maximumKeys) {
        if (maximumKeys < 1) {
            throw new IllegalArgumentException("maximumKeys must be positive");
        }
        this.clock = clock;
        this.maximumKeys = maximumKeys;
    }

    public synchronized void acquire(String purpose, String identity) {
        // 입력을 작은 고정 범위로 제한한다. 이메일 대소문자와 앞뒤 공백으로 제한을 우회하지 못한다.
        AttemptKey key = new AttemptKey(canonicalPart(purpose, 64), canonicalPart(identity, 254));
        // 시스템 시각이 뒤로 보정되어도 예약 순서가 뒤집히거나 활성 기록이 조기 만료되지 않는다.
        Instant measured = clock.instant();
        Instant now = measured.isBefore(latestObserved) ? latestObserved : measured;
        latestObserved = now;
        Instant cutoff = now.minus(WINDOW);
        cleanup(now, cutoff, false);

        ArrayDeque<Instant> window = attempts.get(key);
        if (window == null) {
            // 새 키를 무한히 생성하는 요청은 기존 항목을 밀어내지 않고 실패시킨다.
            if (attempts.size() >= maximumKeys) {
                cleanup(now, cutoff, true);
            }
            if (attempts.size() >= maximumKeys) {
                Instant firstAvailable = attempts.values().stream()
                        .map(values -> values.getLast().plus(WINDOW))
                        .min(Instant::compareTo)
                        .orElse(now.plus(WINDOW));
                throw exceeded(retryAfter(now, firstAvailable));
            }
            window = new ArrayDeque<>(MAX_ATTEMPTS);
            attempts.put(key, window);
        }

        // 최근 15분만 남긴 뒤 검사 전에 횟수를 예약한다. 동시 요청과 성공한 요청도 같은 한도를 사용한다.
        while (!window.isEmpty() && !window.getFirst().isAfter(cutoff)) {
            window.removeFirst();
        }
        if (window.size() >= MAX_ATTEMPTS) {
            throw exceeded(retryAfter(now, window.getFirst().plus(WINDOW)));
        }
        window.addLast(now);
    }

    private void cleanup(Instant now, Instant cutoff, boolean force) {
        if (!force && now.isBefore(nextCleanup)) {
            return;
        }
        attempts.entrySet().removeIf(entry -> !entry.getValue().getLast().isAfter(cutoff));
        nextCleanup = now.plus(CLEANUP_INTERVAL);
    }

    private String canonicalPart(String value, int maximumLength) {
        if (value == null || value.length() > maximumLength + 16) {
            throw invalidIdentity();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw invalidIdentity();
        }
        return normalized;
    }

    private int retryAfter(Instant now, Instant availableAt) {
        long millis = Duration.between(now, availableAt).toMillis();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, (millis + 999) / 1000));
    }

    private AuthException exceeded(int retryAfterSeconds) {
        return new AuthException(HttpStatus.TOO_MANY_REQUESTS, "AUTH_PASSWORD_ATTEMPTS_EXCEEDED",
                "비밀번호 확인 요청이 너무 많습니다. 잠시 후 다시 시도해주세요.", retryAfterSeconds);
    }

    private AuthException invalidIdentity() {
        return new AuthException(HttpStatus.BAD_REQUEST, "AUTH_INVALID_INPUT", "인증 요청 정보를 확인해주세요.");
    }

    private record AttemptKey(String purpose, String identity) {
    }
}
