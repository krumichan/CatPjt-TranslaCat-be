package jp.co.translacat.domain.user.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthAttemptLimiterTest {
    private static final Instant START = Instant.parse("2026-10-11T10:00:00Z");
    private MutableClock clock;
    private AuthAttemptLimiter limiter;

    @BeforeEach
    void prepare() {
        clock = new MutableClock();
        limiter = new AuthAttemptLimiter(clock, 10_000);
    }

    @Test
    void reservesTenChecksIncludingSuccessfulCallsAndRejectsTheEleventh() {
        // 준비 및 실행: 검사 결과를 되돌리는 API 없이 모든 예약을 유지한다.
        for (int attempt = 0; attempt < 10; attempt++) {
            limiter.acquire("login", "person@example.com");
        }

        // 검증
        assertThatThrownBy(() -> limiter.acquire("login", "person@example.com"))
                .isInstanceOfSatisfying(AuthException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(error.getErrorCode()).isEqualTo("AUTH_PASSWORD_ATTEMPTS_EXCEEDED");
                    assertThat(error.getRetryAfterSeconds()).isEqualTo(900);
                });
    }

    @Test
    void emailCaseAndWhitespaceCannotCreateAnotherBucketButPurposesAreIndependent() {
        // 준비
        for (int attempt = 0; attempt < 10; attempt++) {
            limiter.acquire("login", "person@example.com");
        }

        // 실행 및 검증
        assertThatThrownBy(() -> limiter.acquire("LOGIN", "  PERSON@example.com  "))
                .isInstanceOf(AuthException.class);
        limiter.acquire("google-link", "person@example.com");
        limiter.acquire("google-link", "42");
    }

    @Test
    void slidingWindowReleasesOnlyTheChecksThatActuallyExpired() {
        // 준비: 1분 간격으로 열 번 검사해 마지막 요청은 9분에 기록된다.
        for (int attempt = 0; attempt < 10; attempt++) {
            clock.now.set(START.plusSeconds(attempt * 60L));
            limiter.acquire("login", "person@example.com");
        }

        // 실행: 15분 경계에서는 맨 처음 예약 한 건만 만료된다.
        clock.now.set(START.plusSeconds(900));
        limiter.acquire("login", "person@example.com");

        // 검증: 고정 구간 초기화로 열 건을 더 허용하지 않는다.
        assertThatThrownBy(() -> limiter.acquire("login", "person@example.com"))
                .isInstanceOfSatisfying(AuthException.class, error ->
                        assertThat(error.getRetryAfterSeconds()).isEqualTo(60));
        clock.now.set(START.plusSeconds(960));
        limiter.acquire("login", "person@example.com");
    }

    @Test
    void concurrentRequestsCannotReserveMoreThanTenChecks() throws Exception {
        // 준비
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(16);
        var futures = new ArrayList<Future<Boolean>>();
        try {
            for (int request = 0; request < 80; request++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        limiter.acquire("login", "person@example.com");
                        return true;
                    } catch (AuthException limit) {
                        assertThat(limit.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                        return false;
                    }
                }));
            }

            // 실행
            start.countDown();
            int accepted = 0;
            for (Future<Boolean> result : futures) {
                if (result.get(10, TimeUnit.SECONDS)) {
                    accepted++;
                }
            }

            // 검증
            assertThat(accepted).isEqualTo(10);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void fullCapacityFailsClosedWithoutEvictingActiveKeysAndRecoversAfterExpiry() {
        // 준비: 작은 용량으로 포화 상태를 재현한다.
        limiter = new AuthAttemptLimiter(clock, 2);
        for (int attempt = 0; attempt < 10; attempt++) {
            limiter.acquire("login", "first@example.com");
        }
        limiter.acquire("login", "second@example.com");

        // 실행 및 검증: 새 이메일이 기존의 실패 기록을 밀어내지 못한다.
        assertThatThrownBy(() -> limiter.acquire("login", "third@example.com"))
                .isInstanceOfSatisfying(AuthException.class, error ->
                        assertThat(error.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> limiter.acquire("login", "first@example.com")).isInstanceOf(AuthException.class);

        // 실행 및 검증: 보관 기간이 지난 키를 청소하면 다시 예약할 수 있다.
        clock.now.set(START.plusSeconds(900));
        limiter.acquire("login", "third@example.com");
        limiter.acquire("login", "fourth@example.com");
    }

    @Test
    void malformedOrOversizedIdentitiesCannotOccupyLimiterCapacity() {
        // 준비
        limiter = new AuthAttemptLimiter(clock, 1);

        // 실행 및 검증
        assertThatThrownBy(() -> limiter.acquire("login", "x".repeat(10_000))).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> limiter.acquire("login", null)).isInstanceOf(AuthException.class);
        assertThatThrownBy(() -> limiter.acquire(" ", "person@example.com")).isInstanceOf(AuthException.class);
        limiter.acquire("login", "person@example.com");
    }

    @Test
    void retryAfterRoundsUpSoSubsecondExpiryNeverAdvertisesZero() {
        // 준비
        for (int attempt = 0; attempt < 10; attempt++) {
            limiter.acquire("login", "person@example.com");
        }
        clock.now.set(START.plusMillis(899_999));

        // 실행 및 검증
        assertThatThrownBy(() -> limiter.acquire("login", "person@example.com"))
                .isInstanceOfSatisfying(AuthException.class, error ->
                        assertThat(error.getRetryAfterSeconds()).isEqualTo(1));
    }

    @Test
    void backwardClockAdjustmentCannotExpireRecentlyReservedChecksEarly() {
        // 준비: 이전에 관찰한 시각보다 서버 시계가 뒤로 보정된다.
        limiter.acquire("login", "clock-observer@example.com");
        clock.now.set(START.minusSeconds(901));
        for (int attempt = 0; attempt < 10; attempt++) {
            limiter.acquire("login", "person@example.com");
        }

        // 실행 및 검증: 시계가 복구되어도 방금 예약한 열 건이 사라지지 않는다.
        clock.now.set(START);
        assertThatThrownBy(() -> limiter.acquire("login", "person@example.com"))
                .isInstanceOf(AuthException.class);
    }

    private static class MutableClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(START);

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
