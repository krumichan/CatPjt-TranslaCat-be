package jp.co.translacat.domain.user.pushinstallation;

import jp.co.translacat.domain.user.auth.session.AuthSession;
import jp.co.translacat.domain.user.auth.session.AuthSessionRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static jp.co.translacat.domain.user.pushinstallation.PushInstallationDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class PushInstallationSessionBoundaryTest {
    @Test void session_expiring_while_waiting_for_a_lock_is_checked_at_acquisition_time() {
        // 준비: 락 응답이 돌아오기 직전에 시계를 전진시켜 대기 중 만료를 결정적으로 표현한다.
        Instant start = Instant.parse("2026-10-20T00:00:00Z");
        var time = new TestClock(start);
        var rows = mock(PushInstallationRepository.class);
        var guards = mock(PushRegistryLockRepository.class);
        var sessions = mock(AuthSessionRepository.class);
        var actor = new Actor(11, "33333333-3333-4333-8333-333333333333");
        when(guards.lockRegistry()).thenReturn(Optional.of(new PushRegistryLock(1)));
        when(sessions.lockByIdAndUserId(actor.sid(), actor.userId())).thenAnswer(call -> {
            time.now = start.plusSeconds(2);
            return Optional.of(AuthSession.create(actor.sid(), actor.userId(), "f".repeat(64), start, start.plusSeconds(1)));
        });
        var properties = new PushInstallationProperties();
        properties.setEnabled(true);
        properties.setAllowedProjects(Map.of("development", List.of("11111111-1111-4111-8111-111111111111")));
        var service = new PushInstallationService(rows, guards, sessions, properties, time);

        // 실행 / 검증: 요청 시작 시점에는 살아 있어도 락 획득 뒤 만료되면 쓰지 않는다.
        assertThatThrownBy(() -> service.put(actor, "22222222-2222-4222-8222-222222222222",
                new Put("expo", "ExpoPushToken[Synthetic]", "android", "development",
                        "11111111-1111-4111-8111-111111111111", "a".repeat(64), 1)))
                .isInstanceOfSatisfying(PushInstallationException.class, error -> assertThat(error.code()).isEqualTo("PUSH_AUTH_REQUIRED"));
        verifyNoInteractions(rows);
    }

    private static final class TestClock extends Clock {
        private Instant now;
        private TestClock(Instant now) { this.now = now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
