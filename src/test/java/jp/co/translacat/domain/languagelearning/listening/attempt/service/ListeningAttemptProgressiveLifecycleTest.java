package jp.co.translacat.domain.languagelearning.listening.attempt.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import jp.co.translacat.domain.languagelearning.activity.service.LearningActivityCommandService;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.listening.attempt.entity.ListeningItemAttempt;
import jp.co.translacat.domain.languagelearning.listening.attempt.repository.ListeningItemAttemptRepository;
import jp.co.translacat.domain.languagelearning.listening.audio.port.ListeningAudioStoragePort;
import jp.co.translacat.domain.languagelearning.listening.audio.service.ListeningAudioKeyFactory;
import jp.co.translacat.domain.languagelearning.listening.audio.validator.ListeningAudioValidator;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningEvaluationPurpose;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningSessionStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningDailySet;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningItem;
import jp.co.translacat.domain.languagelearning.listening.evaluation.repository.ListeningTaskEvaluationRepository;
import jp.co.translacat.domain.languagelearning.listening.evaluation.service.ListeningAttemptFinalizationCommandService;
import jp.co.translacat.domain.languagelearning.listening.outbox.service.ListeningOutboxCommandService;
import jp.co.translacat.domain.languagelearning.listening.playback.repository.ListeningPlaybackEventRepository;
import jp.co.translacat.domain.languagelearning.listening.policy.ListeningIdempotencyPolicy;
import jp.co.translacat.domain.languagelearning.listening.policy.ListeningIndependencePolicy;
import jp.co.translacat.domain.languagelearning.listening.policy.ListeningProfilePolicy;
import jp.co.translacat.domain.languagelearning.listening.policy.ListeningProgressPolicy;
import jp.co.translacat.domain.languagelearning.listening.policy.ListeningTaskSelectionPolicy;
import jp.co.translacat.domain.languagelearning.listening.profile.repository.ListeningMetricHistoryRepository;
import jp.co.translacat.domain.languagelearning.listening.response.entity.ListeningTaskResponse;
import jp.co.translacat.domain.languagelearning.listening.response.repository.ListeningTaskResponseRepository;
import jp.co.translacat.domain.languagelearning.listening.service.ListeningViewMapper;
import jp.co.translacat.domain.languagelearning.listening.session.entity.ListeningSession;
import jp.co.translacat.domain.languagelearning.listening.session.service.ListeningSessionLockService;
import jp.co.translacat.domain.languagelearning.listening.setting.entity.ListeningPolicySetting;
import jp.co.translacat.domain.languagelearning.listening.setting.service.ListeningPolicySettingQueryService;
import jp.co.translacat.domain.user.entity.User;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListeningAttemptProgressiveLifecycleTest {

    private final ListeningItemAttemptRepository attempts = mock(ListeningItemAttemptRepository.class);
    private final ListeningTaskResponseRepository responses = mock(ListeningTaskResponseRepository.class);
    private final ListeningSessionLockService locks = mock(ListeningSessionLockService.class);
    private final ListeningPolicySettingQueryService settings = mock(ListeningPolicySettingQueryService.class);
    private final ListeningDailySet dailySet = mock(ListeningDailySet.class);
    private final List<ListeningItemAttempt> stored = new ArrayList<>();
    private final ListeningSession session;

    ListeningAttemptProgressiveLifecycleTest() {
        when(dailySet.getTargetItemCount()).thenReturn(5);
        session = ListeningSession.create(mock(User.class), dailySet, "[\"SUMMARY\"]",
                "{}", "[]", "session-key", LocalDateTime.now());
        ReflectionTestUtils.setField(session, "id", 30L);
        when(attempts.findAllLockedBySessionIdOrderByItemItemIndexAscAttemptNoAsc(30L))
                .thenAnswer(invocation -> List.copyOf(stored));
        ListeningPolicySetting policy = mock(ListeningPolicySetting.class);
        when(policy.getResumeHours()).thenReturn(24);
        when(settings.get()).thenReturn(policy);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4, 5})
    void submittingEveryAttachedItemOnlyStartsSessionEvaluationWhenAllFiveExist(int attachedCount) {
        for (int index = 1; index <= attachedCount; index++) {
            ListeningItemAttempt attempt = attempt(index, ListeningEvaluationPurpose.OFFICIAL);
            ListeningTaskResponse response = ListeningTaskResponse.selected(attempt, ListeningTaskType.SUMMARY, "r:" + index);
            ReflectionTestUtils.setField(response, "id", 100L + index);
            response.updateText("요약 답변", "요약 답변");
            when(responses.findAllLockedByAttemptIdOrderByTaskTypeAsc((long) index)).thenReturn(List.of(response));
        }

        ListeningAttemptCommandService command = command();
        for (int index = 1; index <= attachedCount; index++) {
            command.submit(10L, (long) index, null);
        }

        assertThat(session.getStatus()).isEqualTo(attachedCount == 5
                ? ListeningSessionStatus.EVALUATING : ListeningSessionStatus.IN_PROGRESS);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4, 5})
    void skippingEveryAttachedItemCannotCompleteMissingSlots(int attachedCount) {
        for (int index = 1; index <= attachedCount; index++) {
            attempt(index, ListeningEvaluationPurpose.OFFICIAL);
        }
        ListeningAttemptCommandService command = command();
        for (int index = 1; index <= attachedCount; index++) {
            command.skip(10L, (long) index, null);
        }

        assertThat(session.getStatus()).isEqualTo(attachedCount == 5
                ? ListeningSessionStatus.COMPLETED : ListeningSessionStatus.IN_PROGRESS);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4, 5})
    void backgroundFinalizationCannotCompleteMissingSlots(int attachedCount) {
        for (int index = 1; index <= attachedCount; index++) {
            ListeningItemAttempt attempt = attempt(index, ListeningEvaluationPurpose.OFFICIAL);
            ListeningTaskResponse response = ListeningTaskResponse.selected(attempt, ListeningTaskType.SUMMARY, "r:" + index);
            response.markNotEvaluable();
            when(responses.findAllLockedByAttemptIdOrderByTaskTypeAsc((long) index)).thenReturn(List.of(response));
        }
        ListeningAttemptFinalizationCommandService finalizer = finalizer();
        for (int index = 1; index <= attachedCount; index++) {
            assertThat(finalizer.finalizeIfTerminal((long) index)).isTrue();
        }

        assertThat(session.getStatus()).isEqualTo(attachedCount == 5
                ? ListeningSessionStatus.COMPLETED : ListeningSessionStatus.IN_PROGRESS);
    }

    @Test
    void completedPracticeAttemptsCannotFillMissingOfficialSlotsDuringFinalization() {
        ListeningItemAttempt first = attempt(1, ListeningEvaluationPurpose.OFFICIAL);
        ListeningTaskResponse response = ListeningTaskResponse.selected(first, ListeningTaskType.SUMMARY, "r:1");
        response.markNotEvaluable();
        when(responses.findAllLockedByAttemptIdOrderByTaskTypeAsc(1L)).thenReturn(List.of(response));
        for (int index = 2; index <= 5; index++) {
            attempt(index, ListeningEvaluationPurpose.PRACTICE).skip(LocalDateTime.now());
        }

        finalizer().finalizeIfTerminal(1L);

        assertThat(first.isFinalized()).isTrue();
        assertThat(session.getStatus()).isEqualTo(ListeningSessionStatus.IN_PROGRESS);
    }

    @Test
    void lateEvaluationRetainsProgressWithoutReopeningAnExpiredSession() {
        ListeningItemAttempt first = attempt(1, ListeningEvaluationPurpose.OFFICIAL);
        first.submit("[]", 1000L, LocalDateTime.now());
        first.markEvaluating();
        ListeningTaskResponse response = ListeningTaskResponse.selected(first, ListeningTaskType.SUMMARY, "r:1");
        response.markNotEvaluable();
        when(responses.findAllLockedByAttemptIdOrderByTaskTypeAsc(1L)).thenReturn(List.of(response));
        session.abandon(LocalDateTime.now());

        assertThat(finalizer().finalizeIfTerminal(1L)).isTrue();

        assertThat(first.isFinalized()).isTrue();
        assertThat(first.isProgressApplied()).isTrue();
        assertThat(session.getCompletedItemCount()).isEqualTo(1);
        assertThat(session.getStatus()).isEqualTo(ListeningSessionStatus.ABANDONED);
        assertThat(session.getActiveKey()).isNull();
    }

    private ListeningItemAttempt attempt(int index, ListeningEvaluationPurpose purpose) {
        ListeningItem item = mock(ListeningItem.class);
        when(item.getItemIndex()).thenReturn(index);
        when(item.getDailySet()).thenReturn(dailySet);
        ListeningItemAttempt attempt = ListeningItemAttempt.create(session, item, 1, purpose,
                "a:" + index, LocalDateTime.now());
        ReflectionTestUtils.setField(attempt, "id", (long) index);
        when(locks.ownedAttempt(10L, (long) index)).thenReturn(attempt);
        when(locks.attempt((long) index)).thenReturn(attempt);
        stored.add(attempt);
        return attempt;
    }

    private ListeningAttemptCommandService command() {
        return new ListeningAttemptCommandService(attempts, responses, locks, settings,
                new ListeningTaskSelectionPolicy(), new ListeningIdempotencyPolicy(),
                mock(ListeningOutboxCommandService.class), mock(ListeningAttemptFinalizationCommandService.class),
                mock(ListeningAudioStoragePort.class), mock(ListeningAudioValidator.class),
                mock(ListeningAudioKeyFactory.class), mock(ListeningViewMapper.class),
                new LanguageLearningJsonCodec(new ObjectMapper()));
    }

    private ListeningAttemptFinalizationCommandService finalizer() {
        return new ListeningAttemptFinalizationCommandService(attempts, responses, locks,
                mock(ListeningTaskEvaluationRepository.class), mock(ListeningMetricHistoryRepository.class),
                mock(ListeningOutboxCommandService.class), new ListeningProgressPolicy(),
                new ListeningIndependencePolicy(), mock(ListeningPlaybackEventRepository.class),
                new ListeningProfilePolicy(), mock(LearningActivityCommandService.class));
    }
}
