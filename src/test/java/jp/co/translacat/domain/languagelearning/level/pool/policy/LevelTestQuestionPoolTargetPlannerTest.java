package jp.co.translacat.domain.languagelearning.level.pool.policy;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemType;
import jp.co.translacat.domain.languagelearning.level.policy.LevelTestRecipe;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LevelTestQuestionPoolTargetPlannerTest {

    private final LevelTestQuestionPoolTargetPlanner planner =
            new LevelTestQuestionPoolTargetPlanner(new LevelTestRecipe());

    @Test
    void distributes1000QuestionsAcrossRecipeAndFiveBands() {
        var plan = planner.plan(1000);

        assertThat(plan.stream()
                .mapToInt(LevelTestQuestionPoolTargetPlanner.BucketTarget::targetCount)
                .sum()).isEqualTo(1000);
        assertThat(planner.bucketTarget(
                1000,
                LevelTestDomain.VOCABULARY,
                LevelTestItemType.VOCAB_CONTEXT_CHOICE,
                1
        )).isEqualTo(20);
        assertThat(planner.bucketTarget(
                1000,
                LevelTestDomain.LISTENING,
                LevelTestItemType.LISTENING_GIST_CHOICE,
                3
        )).isEqualTo(10);
    }

    @Test
    void preservesExactRequestedTotalForNonRoundTarget() {
        assertThat(planner.plan(1234).stream()
                .mapToInt(LevelTestQuestionPoolTargetPlanner.BucketTarget::targetCount)
                .sum()).isEqualTo(1234);
    }
}
