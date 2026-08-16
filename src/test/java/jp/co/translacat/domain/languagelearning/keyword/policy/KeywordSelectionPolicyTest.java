package jp.co.translacat.domain.languagelearning.keyword.policy;

import jp.co.translacat.domain.languagelearning.ai.dto.model.SelectedKeywordDto;
import jp.co.translacat.domain.languagelearning.common.enums.KeywordSource;
import jp.co.translacat.domain.languagelearning.common.enums.KeywordType;
import jp.co.translacat.domain.languagelearning.keyword.model.SelectedKeywordCandidate;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordSelectionPolicyTest {

    private final KeywordSelectionPolicy policy = new KeywordSelectionPolicy();

    @Test
    void selectsAtMostConfiguredLimit() {
        List<SelectedKeywordCandidate> selected = policy.select(
                candidates(),
                2,
                new Random(1)
        );

        assertThat(selected).hasSize(2);
    }

    @Test
    void keepsTopicAndVocabularyWhenBothTypesExist() {
        List<SelectedKeywordCandidate> selected = policy.select(
                candidates(),
                2,
                new Random(1)
        );

        assertThat(selected)
                .extracting(SelectedKeywordCandidate::type)
                .containsExactlyInAnyOrder(
                        KeywordType.TOPIC,
                        KeywordType.VOCABULARY
                );
    }

    @Test
    void allowsLearningWithoutSelectedKeywords() {
        List<SelectedKeywordCandidate> selected = policy.select(
                List.of(),
                5,
                new Random(1)
        );

        assertThat(selected).isEmpty();
    }

    private List<SelectedKeywordCandidate> candidates() {
        return List.of(
                candidate("it", KeywordType.TOPIC, 1.0),
                candidate("business", KeywordType.TOPIC, 1.0),
                candidate("database", KeywordType.VOCABULARY, 1.0),
                candidate("deployment", KeywordType.VOCABULARY, 1.0)
        );
    }

    private SelectedKeywordCandidate candidate(
            String text,
            KeywordType type,
            double weight
    ) {
        SelectedKeywordDto keyword = new SelectedKeywordDto(
                text,
                text,
                KeywordSource.CUSTOM,
                type,
                text,
                weight
        );

        return new SelectedKeywordCandidate(
                keyword,
                type,
                weight
        );
    }
}
