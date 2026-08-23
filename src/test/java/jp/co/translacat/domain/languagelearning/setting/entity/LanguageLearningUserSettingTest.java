package jp.co.translacat.domain.languagelearning.setting.entity;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageLearningUserSettingTest {

    @Test
    void settingChangeStartsNextDay() {
        LanguageLearningUserSetting setting = LanguageLearningUserSetting.create(
                createUser(),
                5
        );
        LocalDate today = LocalDate.of(2026, 8, 12);

        setting.scheduleUpdate(
                "ko",
                "ja",
                "Asia/Tokyo",
                10,
                today.plusDays(1)
        );

        assertThat(setting.promoteIfEffective(today)).isFalse();
        assertThat(setting.getDailySentenceCount()).isEqualTo(5);

        assertThat(setting.promoteIfEffective(today.plusDays(1))).isTrue();
        assertThat(setting.getOriginLanguage()).isEqualTo("ko");
        assertThat(setting.getLearningLanguage()).isEqualTo("ja");
        assertThat(setting.getDailySentenceCount()).isEqualTo(10);
    }

    @Test
    void adminClampCorrectsActiveAndPending() {
        LanguageLearningUserSetting setting = LanguageLearningUserSetting.create(
                createUser(),
                15
        );

        setting.scheduleUpdate(
                null,
                null,
                null,
                18,
                LocalDate.of(2026, 8, 13)
        );
        setting.clampActiveAndPending(1, 10);

        assertThat(setting.getDailySentenceCount()).isEqualTo(10);
        assertThat(setting.getPendingDailySentenceCount()).isEqualTo(10);
    }

    @Test
    void speakingGoalStartsNextDayButPlaybackIsImmediate() {
        LanguageLearningUserSetting setting = LanguageLearningUserSetting.create(
                createUser(),
                5
        );
        LocalDate today = LocalDate.of(2026, 8, 15);

        setting.scheduleUpdate(
                null,
                null,
                null,
                null,
                12,
                today.plusDays(1)
        );
        setting.updateSpeakingPlayback("Aoede", "SLOW");

        assertThat(setting.getDailySpeakingGoalMinutes()).isEqualTo(5);
        assertThat(setting.getPendingDailySpeakingGoalMinutes()).isEqualTo(12);
        assertThat(setting.getSpeakingVoiceId()).isEqualTo("Aoede");
        assertThat(setting.getSpeakingPlaybackSpeed()).isEqualTo("SLOW");

        setting.promoteIfEffective(today.plusDays(1));

        assertThat(setting.getDailySpeakingGoalMinutes()).isEqualTo(12);
    }

    @Test
    void speakingGoalClampCorrectsActiveAndPending() {
        LanguageLearningUserSetting setting = LanguageLearningUserSetting.create(
                createUser(),
                5
        );
        setting.scheduleUpdate(
                null,
                null,
                null,
                null,
                25,
                LocalDate.of(2026, 8, 16)
        );

        setting.clampSpeakingGoalActiveAndPending(3, 20);

        assertThat(setting.getDailySpeakingGoalMinutes()).isEqualTo(5);
        assertThat(setting.getPendingDailySpeakingGoalMinutes()).isEqualTo(20);
    }


    @Test
    void listeningGoalStartsNextDayButDefaultTasksAreImmediate() {
        LanguageLearningUserSetting setting = LanguageLearningUserSetting.create(
                createUser(),
                5
        );
        LocalDate today = LocalDate.of(2026, 8, 23);

        setting.scheduleListeningGoal(12, today.plusDays(1));
        setting.updateDefaultListeningTaskTypes(
                "[\"DICTATION\",\"REPEAT_AFTER_AUDIO\"]"
        );

        assertThat(setting.getDailyListeningGoalCount()).isEqualTo(5);
        assertThat(setting.getPendingDailyListeningGoalCount()).isEqualTo(12);
        assertThat(setting.getDefaultListeningTaskTypesJson())
                .isEqualTo("[\"DICTATION\",\"REPEAT_AFTER_AUDIO\"]");

        setting.promoteIfEffective(today.plusDays(1));

        assertThat(setting.getDailyListeningGoalCount()).isEqualTo(12);
        assertThat(setting.getPendingDailyListeningGoalCount()).isNull();
    }

    @Test
    void listeningGoalClampCorrectsActiveAndPending() {
        LanguageLearningUserSetting setting = LanguageLearningUserSetting.create(
                createUser(),
                5
        );
        setting.initializeListening(25, null);
        setting.scheduleListeningGoal(30, LocalDate.of(2026, 8, 24));

        setting.clampListeningGoalActiveAndPending(1, 20);

        assertThat(setting.getDailyListeningGoalCount()).isEqualTo(20);
        assertThat(setting.getPendingDailyListeningGoalCount()).isEqualTo(20);
    }

    private User createUser() {
        return User.createLocalUser(
                "ll@test.local",
                "pw",
                "ll",
                Role.USER,
                "LLUSER0001"
        );
    }
}
