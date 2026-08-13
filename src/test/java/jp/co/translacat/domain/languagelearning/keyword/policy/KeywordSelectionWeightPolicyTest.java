package jp.co.translacat.domain.languagelearning.keyword.policy;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordSelectionWeightPolicyTest {

    private final KeywordSelectionWeightPolicy policy =
            new KeywordSelectionWeightPolicy();

    private final LocalDate baseDate = LocalDate.of(2026, 8, 12);

    @Test
    void newKeywordRampsUpForSevenDays() {
        LocalDateTime availableFrom = baseDate.atStartOfDay();

        assertThat(policy.calculateRampWeight(
                availableFrom,
                baseDate
        )).isEqualTo(0.25);
        assertThat(policy.calculateRampWeight(
                availableFrom,
                baseDate.plusDays(1)
        )).isEqualTo(0.50);
        assertThat(policy.calculateRampWeight(
                availableFrom,
                baseDate.plusDays(3)
        )).isEqualTo(0.75);
        assertThat(policy.calculateRampWeight(
                availableFrom,
                baseDate.plusDays(6)
        )).isEqualTo(1.0);
    }
}
