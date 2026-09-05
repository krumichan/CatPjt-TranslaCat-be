package jp.co.translacat.domain.languagelearning.level.policy;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemType;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;

import static org.assertj.core.api.Assertions.assertThat;

class LevelTestRecipeTest {

    private final LevelTestRecipe recipe = new LevelTestRecipe();

    @Test
    void keepsFixedTwentyQuestionDistribution() {
        var counts = new EnumMap<LevelTestDomain, Integer>(
                LevelTestDomain.class
        );
        for (int questionNumber = 1;
             questionNumber <= LevelTestRecipe.TOTAL_QUESTIONS;
             questionNumber++) {
            counts.merge(
                    recipe.entry(questionNumber).domain(),
                    1,
                    Integer::sum
            );
        }

        assertThat(LevelTestRecipe.TOTAL_QUESTIONS).isEqualTo(20);
        assertThat(recipe.entry(15).itemType()).isEqualTo(LevelTestItemType.WRITING_TRANSLATION);
        assertThat(recipe.entry(16).itemType()).isEqualTo(LevelTestItemType.WRITING_TRANSLATION);
        assertThat(recipe.entry(17).itemType()).isEqualTo(LevelTestItemType.WRITING_SHORT_PARAGRAPH);
        assertThat(recipe.entry(18).itemType()).isEqualTo(LevelTestItemType.SPEAKING_REPEAT);
        assertThat(recipe.entry(19).itemType()).isEqualTo(LevelTestItemType.SPEAKING_REPEAT);
        assertThat(recipe.entry(20).itemType()).isEqualTo(LevelTestItemType.SPEAKING_GUIDED_RESPONSE);
        assertThat(counts)
                .containsEntry(LevelTestDomain.VOCABULARY, 3)
                .containsEntry(LevelTestDomain.GRAMMAR, 3)
                .containsEntry(LevelTestDomain.READING, 4)
                .containsEntry(LevelTestDomain.LISTENING, 4)
                .containsEntry(LevelTestDomain.WRITING, 3)
                .containsEntry(LevelTestDomain.SPEAKING, 3);
    }
}
