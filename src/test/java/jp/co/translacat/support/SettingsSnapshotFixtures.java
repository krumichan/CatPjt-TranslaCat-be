package jp.co.translacat.support;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.setting.dto.response.AdminSettingResponseDto;
import jp.co.translacat.domain.languagelearning.setting.dto.response.UserSettingResponseDto;
import jp.co.translacat.domain.languagelearning.setting.model.AdminSettingsSnapshot;
import jp.co.translacat.domain.languagelearning.setting.model.UserSettingsSnapshot;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 이식 전 테스트의 기본값 fixture다. 운영 gateway의 fallback으로 사용하지 않는다.
 */
public final class SettingsSnapshotFixtures {
    private SettingsSnapshotFixtures() {
    }

    public static AdminSettingsSnapshot admin() {
        return new AdminSettingsSnapshot(new AdminSettingResponseDto(
                5, 1, 20, 5, 7, 30, true, true, true, true, 5, 3, 20,
                30, 5, 10, 20, 1.0, 60, 10485760L, 7, 30, 2, 2, 1,
                30, 30, 60, 1000, false));
    }

    public static UserSettingResponseDto userDto() {
        return new UserSettingResponseDto("ko", "ja", "Asia/Tokyo", 5, 5, "marin", "NORMAL", 5,
                List.of(ListeningTaskType.DICTATION), null, null, null, null, null, null, null,
                1, 20, 3, 20, 1, 20, true);
    }

    public static UserSettingsSnapshot user(long userId) {
        return new UserSettingsSnapshot(userId, LocalDate.of(2026, 9, 24),
                LocalDateTime.of(2026, 9, 24, 1, 0), userDto());
    }
}
