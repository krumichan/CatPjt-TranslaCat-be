package jp.co.translacat.domain.languagelearning.profile.entity;

import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingDifficulty;
import jp.co.translacat.domain.languagelearning.common.enums.LearningProfileState;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class LearningProfileTest {

    @Test
    void levelTestStartsSevenDayCalibration() {
        LearningProfile profile = LearningProfile.create(user());
        profile.completeLevelTest(62.5);

        assertThat(profile.getState())
                .isEqualTo(LearningProfileState.CALIBRATING);

        LocalDate startDate = LocalDate.of(2026, 8, 12);
        profile.startCalibrationIfNeeded(startDate);
        profile.advanceCalibration(startDate.plusDays(6));

        assertThat(profile.getState())
                .isEqualTo(LearningProfileState.CALIBRATING);

        profile.advanceCalibration(startDate.plusDays(7));

        assertThat(profile.getState())
                .isEqualTo(LearningProfileState.ACTIVE);
    }

    @Test
    void profileUsesWeightedAccumulationInsteadOfOverwrite() {
        LearningProfile profile = LearningProfile.create(user());
        profile.completeLevelTest(50);

        profile.applyScores(
                80,
                80,
                80,
                80,
                80,
                0.5,
                DailyWritingDifficulty.NORMAL
        );
        profile.applyScores(
                20,
                20,
                20,
                20,
                20,
                0.3,
                DailyWritingDifficulty.NORMAL
        );

        assertThat(profile.getMeaningScore()).isEqualTo(62.0);
        assertThat(profile.getGrammarScore()).isEqualTo(62.0);
        assertThat(profile.getEvaluationCount()).isEqualTo(2);
        assertThat(profile.getConfidence()).isEqualTo(0.1);
    }

    private User user() {
        return User.createLocalUser(
                "profile@test.local",
                "pw",
                "profile",
                Role.USER,
                "LLPROFILE1"
        );
    }
}
