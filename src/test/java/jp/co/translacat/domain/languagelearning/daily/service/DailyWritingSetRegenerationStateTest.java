package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingType;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.user.entity.User;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DailyWritingSetRegenerationStateTest {

    @Test
    void readySetCanClaimAndReleaseRegenerationWithoutChangingStatus() {
        DailyWritingSet set = DailyWritingSet.createGenerating(
                mock(User.class),
                LocalDate.of(2026, 9, 12),
                DailyWritingType.TRANSLATION,
                "snapshot",
                5,
                "{}"
        );
        set.ready("prompt-v1");
        LocalDateTime now = LocalDateTime.now();

        assertThat(set.canClaimRegeneration(now)).isTrue();

        set.claimRegeneration("regen-token", now.plusMinutes(10));

        assertThat(set.isRegenerationActive(now.plusMinutes(1)))
                .isTrue();
        assertThat(set.ownsRegeneration("regen-token")).isTrue();
        assertThat(set.getStatus().name()).isEqualTo("READY");

        set.releaseRegeneration("regen-token");

        assertThat(set.isRegenerationActive(now.plusMinutes(1)))
                .isFalse();
        assertThat(set.ownsRegeneration("regen-token")).isFalse();
    }

    @Test
    void expiredRegenerationLeaseCanBeReclaimed() {
        DailyWritingSet set = DailyWritingSet.createGenerating(
                mock(User.class),
                LocalDate.of(2026, 9, 12),
                DailyWritingType.TRANSLATION,
                "snapshot",
                5,
                "{}"
        );
        set.ready("prompt-v1");
        LocalDateTime now = LocalDateTime.now();
        set.claimRegeneration(
                "expired-token",
                now.minusSeconds(1)
        );

        assertThat(set.isRegenerationActive(now)).isFalse();
        assertThat(set.canClaimRegeneration(now)).isTrue();
    }
}
