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
