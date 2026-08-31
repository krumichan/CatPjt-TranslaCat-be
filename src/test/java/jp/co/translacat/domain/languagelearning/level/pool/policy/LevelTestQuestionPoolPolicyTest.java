package jp.co.translacat.domain.languagelearning.level.pool.policy;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemType;
import jp.co.translacat.domain.languagelearning.level.policy.LevelTestRecipe;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningAdminSettingQueryService;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LevelTestQuestionPoolPolicyTest {

    @Test
    void reusesOnlyAfterAdminTargetAndPlannedBucketTargetAreReady() {
        LanguageLearningAdminSettingQueryService settings =
                mock(LanguageLearningAdminSettingQueryService.class);
        when(settings.getLevelTestQuestionPoolTargetSize())
                .thenReturn(1000);
        LevelTestQuestionPoolPolicy policy = new LevelTestQuestionPoolPolicy(
                settings,
                new LevelTestQuestionPoolTargetPlanner(new LevelTestRecipe())
        );

        assertThat(policy.canReuse(
                999,
                100,
                LevelTestDomain.VOCABULARY,
                LevelTestItemType.VOCAB_CONTEXT_CHOICE,
                1
        )).isFalse();
        assertThat(policy.canReuse(
                1000,
                19,
                LevelTestDomain.VOCABULARY,
                LevelTestItemType.VOCAB_CONTEXT_CHOICE,
                1
        )).isFalse();
        assertThat(policy.canReuse(
                1000,
                20,
                LevelTestDomain.VOCABULARY,
                LevelTestItemType.VOCAB_CONTEXT_CHOICE,
                1
        )).isTrue();
    }
}
