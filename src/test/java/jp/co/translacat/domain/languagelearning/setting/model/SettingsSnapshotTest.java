package jp.co.translacat.domain.languagelearning.setting.model;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.setting.dto.response.UserSettingResponseDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettingsSnapshotTest {
    @Test
    void mutableInputListCannotChangeThePublicResponseLater() {
        // 준비
        var tasks = new ArrayList<>(List.of(ListeningTaskType.DICTATION));
        var dto = new UserSettingResponseDto("ko", "ja", "Asia/Tokyo", 5, 5, "marin", "NORMAL", 5,
                tasks, null, null, null, null, null, null, null, 1, 20, 3, 20, 1, 20, true);

        // 실행
        tasks.add(ListeningTaskType.SUMMARY);

        // 검증: 현재 외부 응답이 제공하는 방어적 복사는 유지한다.
        assertEquals(List.of(ListeningTaskType.DICTATION), dto.defaultListeningTaskTypes());
        assertThrows(UnsupportedOperationException.class, () -> dto.defaultListeningTaskTypes().clear());
    }
}
