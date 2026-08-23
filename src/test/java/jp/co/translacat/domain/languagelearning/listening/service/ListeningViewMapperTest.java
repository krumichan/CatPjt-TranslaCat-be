package jp.co.translacat.domain.languagelearning.listening.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ListeningViewMapperTest {

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 8, 23, 20, 0);

    @Test
    void audioIsAvailableUntilRetentionBoundary() {
        var result = ListeningViewMapper.audioAvailability(
                "user/1/audio.webm",
                NOW,
                null,
                NOW
        );

        assertThat(result.available()).isTrue();
        assertThat(result.expired()).isFalse();
    }

    @Test
    void audioIsExpiredAfterRetentionOrDeletion() {
        var retainedExpired = ListeningViewMapper.audioAvailability(
                "user/1/audio.webm",
                NOW.minusSeconds(1),
                null,
                NOW
        );
        var deleted = ListeningViewMapper.audioAvailability(
                null,
                NOW.plusDays(1),
                NOW.minusMinutes(1),
                NOW
        );

        assertThat(retainedExpired.available()).isFalse();
        assertThat(retainedExpired.expired()).isTrue();
        assertThat(deleted.available()).isFalse();
        assertThat(deleted.expired()).isTrue();
    }
}
