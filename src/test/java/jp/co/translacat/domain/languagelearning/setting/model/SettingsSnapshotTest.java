package jp.co.translacat.domain.languagelearning.setting.model;

import jp.co.translacat.domain.languagelearning.setting.dto.response.AdminSettingResponseDto;
import jp.co.translacat.domain.languagelearning.setting.dto.response.UserSettingResponseDto;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.listening.setting.model.ListeningPolicySnapshot;
import jp.co.translacat.support.SettingsSnapshotFixtures;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SettingsSnapshotTest {
    @Test void allAdminPolicyAccessorsPreserveTheReceivedValues() throws Exception {
        var snapshot = SettingsSnapshotFixtures.admin();
        for (var field : AdminSettingResponseDto.class.getRecordComponents()) {
            String method = (field.getType() == boolean.class ? "is" : "get")
                    + Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
            assertEquals(field.getAccessor().invoke(snapshot.settings()), AdminSettingsSnapshot.class.getMethod(method).invoke(snapshot));
        }
    }
    @Test void allUserSettingAccessorsPreserveTheReceivedValues() throws Exception {
        var snapshot = SettingsSnapshotFixtures.user(123);
        for (var field : UserSettingResponseDto.class.getRecordComponents()) {
            if (field.getName().equals("defaultListeningTaskTypes")) continue;
            String method = (field.getType() == boolean.class ? "is" : "get")
                    + Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
            assertEquals(field.getAccessor().invoke(snapshot.settings()), UserSettingsSnapshot.class.getMethod(method).invoke(snapshot));
        }
        assertEquals("[\"DICTATION\"]", snapshot.getDefaultListeningTaskTypesJson());
    }
    @Test void mutableInputListCannotChangeTheSnapshotLater() {
        var tasks = new ArrayList<>(List.of(ListeningTaskType.DICTATION));
        var dto = userWithTasks(tasks);
        tasks.add(ListeningTaskType.SUMMARY);
        assertEquals(List.of(ListeningTaskType.DICTATION), dto.defaultListeningTaskTypes());
        assertThrows(UnsupportedOperationException.class, () -> dto.defaultListeningTaskTypes().clear());
    }
    @Test void snapshotsRejectMissingIdentityRevisionAndPolicy() {
        var value = SettingsSnapshotFixtures.user(123);
        assertThrows(IllegalArgumentException.class, () -> new UserSettingsSnapshot(0L, value.learningDate(), value.revision(), value.settings()));
        assertThrows(NullPointerException.class, () -> new UserSettingsSnapshot(123L, null, value.revision(), value.settings()));
        assertThrows(NullPointerException.class, () -> new UserSettingsSnapshot(123L, value.learningDate(), null, value.settings()));
        assertThrows(NullPointerException.class, () -> new AdminSettingsSnapshot(null));
    }
    @Test void pendingDatesAndTargetsAreNotRecomputedInBe() {
        var dto = new UserSettingResponseDto("ko", "ja", "Asia/Tokyo", 5, 5, "marin", "NORMAL", 5,
                List.of(ListeningTaskType.DICTATION), null, "en", "UTC", 7, 9, 11, LocalDate.of(2026, 9, 25),
                1, 20, 3, 20, 1, 20, true);
        var value = new UserSettingsSnapshot(123L, LocalDate.of(2026, 9, 24), LocalDateTime.of(2026, 9, 24, 2, 0), dto);
        assertEquals(5, value.getDailySentenceCount());
        assertEquals(Integer.valueOf(7), value.getPendingDailySentenceCount());
        assertEquals("UTC", value.getPendingTimezone());
        assertEquals(LocalDate.of(2026, 9, 24), value.learningDate());
    }
    @Test void listeningPolicyUsesRemoteLimitsWithoutLocalDefaults() {
        var value = new ListeningPolicySnapshot(true, 8, 2, 12, 10, 30, 60, 10485760L,
                2, 2, 7, 7, 30, 2, 1, 1, "p2", "m2", false);
        assertEquals(8, value.resolveItemCount(null));
        assertEquals(2, value.resolveItemCount(2));
        assertEquals(10, value.resolveItemCount(10));
        assertThrows(IllegalArgumentException.class, () -> value.resolveItemCount(1));
        assertThrows(IllegalArgumentException.class, () -> value.resolveItemCount(11));
        assertEquals("p2", value.getProfilePolicyVersion());
    }
    @Test void adminClampingUsesOnlyThisReceivedPolicy() {
        var value = SettingsSnapshotFixtures.admin();
        assertEquals(1, value.clampDailySentenceCount(-1));
        assertEquals(9, value.clampDailySentenceCount(9));
        assertEquals(20, value.clampDailySentenceCount(21));
        assertEquals(1000, value.resolvedLevelTestQuestionPoolTargetSize());
        assertFalse(value.resolvedLevelTestQuestionPoolReplenishmentEnabled());
    }
    private UserSettingResponseDto userWithTasks(List<ListeningTaskType> tasks) {
        return new UserSettingResponseDto("ko", "ja", "Asia/Tokyo", 5, 5, "marin", "NORMAL", 5,
                tasks, null, null, null, null, null, null, null, 1, 20, 3, 20, 1, 20, true);
    }
}
