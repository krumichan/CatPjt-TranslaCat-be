package jp.co.translacat.domain.languagelearning.setting.entity;

import jp.co.translacat.global.exception.BusinessException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class LanguageLearningAdminSettingTest {

    @Test
    void docs24DefaultsAreStable() {
        LanguageLearningAdminSetting setting = LanguageLearningAdminSetting.createDefault();

        assertThat(setting.getDefaultDailySentenceCount()).isEqualTo(5);
        assertThat(setting.getMinDailySentenceCount()).isEqualTo(1);
        assertThat(setting.getMaxDailySentenceCount()).isEqualTo(20);
        assertThat(setting.getDailyKeywordMaxCount()).isEqualTo(5);
        assertThat(setting.getReviewAvailableDays()).isEqualTo(7);
        assertThat(setting.getLevelRecheckRecommendationDays()).isEqualTo(30);
        assertThat(setting.isAdaptiveWritingEnabled()).isTrue();
        assertThat(setting.isAiEvaluationEnabled()).isTrue();
    }

    @Test
    void rejectsInvalidBounds() {
        LanguageLearningAdminSetting setting = LanguageLearningAdminSetting.createDefault();

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> setting.update(
                        5,
                        10,
                        3,
                        null,
                        null,
                        null,
                        null,
                        null
                ));
    }

    @Test
    void clampUsesAdminBounds() {
        LanguageLearningAdminSetting setting = LanguageLearningAdminSetting.createDefault();
        setting.update(
                5,
                3,
                10,
                null,
                null,
                null,
                null,
                null
        );

        assertThat(setting.clampDailySentenceCount(1)).isEqualTo(3);
        assertThat(setting.clampDailySentenceCount(50)).isEqualTo(10);
    }
}
