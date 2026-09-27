package jp.co.translacat.support;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.setting.dto.response.AdminSettingResponseDto;
import jp.co.translacat.domain.languagelearning.setting.dto.response.UserSettingResponseDto;

import java.util.List;

/**
 * 현재 외부 Settings 계약의 합성 응답이다. 운영 gateway의 fallback으로 사용하지 않는다.
 */
public final class SettingsSnapshotFixtures {
    private SettingsSnapshotFixtures() {
    }

    public static AdminSettingResponseDto adminDto() {
        return new AdminSettingResponseDto(
                5, 1, 20, 5, 7, 30, true, true, true, true, 5, 3, 20,
                30, 5, 10, 20, 1.0, 60, 10485760L, 7, 30, 2, 2, 1,
                30, 30, 60, 1000, false);
    }

    public static UserSettingResponseDto userDto() {
        return new UserSettingResponseDto("ko", "ja", "Asia/Tokyo", 5, 5, "marin", "NORMAL", 5,
                List.of(ListeningTaskType.DICTATION), null, null, null, null, null, null, null,
                1, 20, 3, 20, 1, 20, true);
    }

}
