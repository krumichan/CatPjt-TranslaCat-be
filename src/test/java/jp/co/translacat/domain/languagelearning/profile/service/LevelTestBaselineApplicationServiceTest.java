package jp.co.translacat.domain.languagelearning.profile.service;

import jp.co.translacat.domain.languagelearning.activity.service.LearningActivityCommandService;
import jp.co.translacat.domain.languagelearning.common.enums.LearningProfileState;
import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionType;
import jp.co.translacat.domain.languagelearning.level.model.LevelCompletionSnapshot;
import jp.co.translacat.domain.languagelearning.profile.entity.LearningProfile;
import jp.co.translacat.domain.languagelearning.profile.entity.LevelTestBaselineReceipt;
import jp.co.translacat.domain.languagelearning.profile.repository.LearningProfileRepository;
import jp.co.translacat.domain.languagelearning.profile.repository.LevelTestBaselineReceiptRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class LevelTestBaselineApplicationServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final LearningProfileRepository profiles = mock(LearningProfileRepository.class);
    private final LevelTestBaselineReceiptRepository receipts = mock(LevelTestBaselineReceiptRepository.class);
    private final LearningActivityCommandService activities = mock(LearningActivityCommandService.class);
    private final LevelTestBaselineApplicationService service =
            new LevelTestBaselineApplicationService(users, profiles, receipts, activities);
    private final LocalDateTime at = LocalDateTime.parse("2026-09-25T01:00:00");
    private final String id = "5f55806e-767d-4c0d-82fd-66e44ee094f2";

    private LevelCompletionSnapshot result(String id, int score, LocalDateTime at) {
        return new LevelCompletionSnapshot(123L, 1L, id, LevelTestSessionType.INITIAL, score,
                score < 70 ? "INTERMEDIATE" : "UPPER_INTERMEDIATE", at.toLocalDate(), at.minusMinutes(10), at);
    }

    private LearningProfile prepare() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(123L);
        when(users.findLockedById(123L)).thenReturn(Optional.of(user));
        LearningProfile profile = LearningProfile.create(user);
        when(profiles.findLockedByUserId(123L)).thenReturn(Optional.of(profile));
        return profile;
    }

    @Test
    void appliesBaselineAndReceiptWithActivity() {
        var profile = prepare();
        var value = result(id, 65, at);
        assertEquals(LearningProfileState.CALIBRATING, service.apply(123L, value));
        assertEquals(65.0, profile.getBaseLevelScore());
        assertEquals(at.toLocalDate(), profile.getCalibrationStartedDate());
        verify(receipts).save(any(LevelTestBaselineReceipt.class));
        verify(activities).getOrCreate(eq(123L), eq(LearningSource.LEVEL_TEST), eq("LL_LEVEL_TEST:" + id),
                eq(at.toLocalDate()), eq("Language Level Test"), eq(600L), eq(at.minusMinutes(10)), eq(at));
    }

    @Test
    void duplicateDoesNotRestartCalibrationOrInsertActivity() {
        var profile = prepare();
        profile.completeLevelTest(65, at.toLocalDate().minusDays(3));
        var value = result(id, 65, at);
        when(receipts.findLockedByUserId(123L)).thenReturn(Optional.of(new LevelTestBaselineReceipt(value, at)));
        service.apply(123L, value);
        assertEquals(at.toLocalDate().minusDays(3), profile.getCalibrationStartedDate());
        verify(receipts, never()).save(any());
        verifyNoInteractions(activities);
    }

    @Test
    void sameCompletionIdWithDifferentContentIsRejected() {
        prepare();
        when(receipts.findLockedByUserId(123L)).thenReturn(
                Optional.of(new LevelTestBaselineReceipt(result(id, 65, at), at)));
        assertThrows(IllegalStateException.class, () -> service.apply(123L, result(id, 66, at)));
        verifyNoInteractions(activities);
    }

    @Test
    void olderCompletionDoesNotOverwriteNewerBaseline() {
        prepare();
        var current = result(id, 65, at);
        when(receipts.findLockedByUserId(123L)).thenReturn(Optional.of(new LevelTestBaselineReceipt(current, at)));
        assertThrows(IllegalStateException.class,
                () -> service.apply(123L, result("821c2f0c-3998-4c19-98a9-87f37bc93511", 70, at.minusDays(1))));
        verifyNoInteractions(activities);
    }

    @Test
    void missingProfileWithExistingReceiptIsNotSilentlyReinitialized() {
        prepare();
        when(profiles.findLockedByUserId(123L)).thenReturn(Optional.empty());
        when(receipts.findLockedByUserId(123L)).thenReturn(
                Optional.of(new LevelTestBaselineReceipt(result(id, 65, at), at)));
        assertThrows(IllegalStateException.class, () -> service.apply(123L, result(id, 65, at)));
        verify(profiles, never()).save(any());
    }

    @Test
    void wrongOwnerIsRejectedBeforeDatabaseAccess() {
        assertThrows(IllegalArgumentException.class, () -> service.apply(456L, result(id, 65, at)));
        verifyNoInteractions(users, profiles, receipts, activities);
    }

    @Test
    void noCompletionDoesNotUnlockLearning() {
        var gateway = mock(jp.co.translacat.domain.languagelearning.level.port.LevelTestGateway.class);
        when(gateway.baseline(123L)).thenReturn(Optional.empty());
        var bridge = new LevelTestBaselineBridge(gateway, service);
        assertThrows(jp.co.translacat.global.exception.BusinessException.class, () -> bridge.requireCompleted(123L));
        verifyNoInteractions(users, profiles, receipts, activities);
    }
}
