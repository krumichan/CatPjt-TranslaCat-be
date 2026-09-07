package jp.co.translacat.domain.languagelearning.practice.entity;

import jp.co.translacat.domain.user.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class VocabularyMasteryTest {
    @Test
    void tracksSelectionAndEvaluationSeparatelyFromTopicKeywordMastery() {
        VocabularyMastery mastery = VocabularyMastery.create(
                mock(User.class),
                "余儀なくされる",
                "余儀なくされる"
        );

        mastery.markSelected(LocalDate.of(2026, 9, 7));
        mastery.applyScore(100, 0.35);
        mastery.applyScore(0, 0.15);

        assertThat(mastery.getSelectedCount()).isEqualTo(1);
        assertThat(mastery.getEvaluationCount()).isEqualTo(2);
        assertThat(mastery.getLastSelectedDate()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(mastery.getScore()).isEqualTo(57.38);
    }
}
