package jp.co.translacat.domain.languagelearning.listening.outbox.entity;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningOutboxStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningOutboxType;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ListeningOutboxEventTest {

    @Test
    void reclaimsOnlyProcessingEventWithoutResettingAttemptCount() {
        LocalDateTime now = LocalDateTime.now();
        ListeningOutboxEvent event = ListeningOutboxEvent.create(
                ListeningOutboxType.GENERATE_TTS,
                1L,
                "{}",
                "key",
                now.minusMinutes(10)
        );
        event.claim();

        event.reclaim(now);

        assertThat(event.getStatus()).isEqualTo(ListeningOutboxStatus.PENDING);
        assertThat(event.getAvailableAt()).isEqualTo(now);
        assertThat(event.getAttemptCount()).isEqualTo(1);
    }
    @Test
    void releaseReturnsOnlyProcessingEventToPendingImmediately() {
        LocalDateTime now = LocalDateTime.now();
        ListeningOutboxEvent event = ListeningOutboxEvent.create(
                ListeningOutboxType.GENERATE_TTS,
                2L,
                "{}",
                "release-key",
                now.minusSeconds(30)
        );
        event.claim();

        event.release(now, "server restart");

        assertThat(event.getStatus()).isEqualTo(ListeningOutboxStatus.PENDING);
        assertThat(event.getAvailableAt()).isEqualTo(now);
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getLastError()).isEqualTo("server restart");
    }

}
