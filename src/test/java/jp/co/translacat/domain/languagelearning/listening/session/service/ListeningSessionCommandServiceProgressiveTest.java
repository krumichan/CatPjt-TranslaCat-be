package jp.co.translacat.domain.languagelearning.listening.session.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.listening.attempt.entity.ListeningItemAttempt;
import jp.co.translacat.domain.languagelearning.listening.attempt.repository.ListeningItemAttemptRepository;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningEvaluationPurpose;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningLearningMode;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningSessionStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningDailySet;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningItem;
import jp.co.translacat.domain.languagelearning.listening.daily.service.ListeningDailySetQueryService;
import jp.co.translacat.domain.languagelearning.listening.dto.ListeningApiContract;
import jp.co.translacat.domain.languagelearning.listening.policy.ListeningIdempotencyPolicy;
import jp.co.translacat.domain.languagelearning.listening.policy.ListeningTaskSelectionPolicy;
import jp.co.translacat.domain.languagelearning.listening.response.repository.ListeningTaskResponseRepository;
import jp.co.translacat.domain.languagelearning.listening.service.ListeningViewMapper;
import jp.co.translacat.domain.languagelearning.listening.session.entity.ListeningSession;
import jp.co.translacat.domain.languagelearning.listening.session.repository.ListeningSessionRepository;
import jp.co.translacat.domain.languagelearning.listening.setting.entity.ListeningPolicySetting;
import jp.co.translacat.domain.languagelearning.listening.setting.service.ListeningPolicySettingQueryService;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningUserSettingQueryService;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.exception.BusinessException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListeningSessionCommandServiceProgressiveTest {

    private final ListeningSessionRepository sessions = mock(ListeningSessionRepository.class);
    private final ListeningItemAttemptRepository attempts = mock(ListeningItemAttemptRepository.class);
    private final ListeningTaskResponseRepository responses = mock(ListeningTaskResponseRepository.class);
    private final ListeningDailySetQueryService sets = mock(ListeningDailySetQueryService.class);
    private final ListeningSessionLockService locks = mock(ListeningSessionLockService.class);
    private final ListeningPolicySettingQueryService settings = mock(ListeningPolicySettingQueryService.class);
    private final ListeningDailySet dailySet = mock(ListeningDailySet.class);
    private final ListeningViewMapper mapper = mock(ListeningViewMapper.class);
    private final List<ListeningItemAttempt> storedAttempts = new ArrayList<>();
    private final List<ListeningItem> activeItems = new ArrayList<>();
    private ListeningSession session;
    private ListeningSessionCommandService service;

    @BeforeEach
    void setUp() {
        when(dailySet.getId()).thenReturn(20L);
        when(dailySet.getTargetItemCount()).thenReturn(5);
        session = ListeningSession.create(mock(User.class), dailySet,
                "[\"SUMMARY\"]", "{}", "[]", "session-key", LocalDateTime.now());
        ReflectionTestUtils.setField(session, "id", 30L);
        when(locks.ownedSession(10L, 30L)).thenReturn(session);
        when(attempts.findAllLockedBySessionIdOrderByItemItemIndexAscAttemptNoAsc(30L))
                .thenAnswer(invocation -> List.copyOf(storedAttempts));
        when(attempts.saveAndFlush(any(ListeningItemAttempt.class))).thenAnswer(invocation -> {
            ListeningItemAttempt attempt = invocation.getArgument(0);
            ReflectionTestUtils.setField(attempt, "id", 100L + storedAttempts.size());
            storedAttempts.add(attempt);
            return attempt;
        });
        when(sets.activeItems(20L)).thenAnswer(invocation -> List.copyOf(activeItems));
        ListeningPolicySetting policy = mock(ListeningPolicySetting.class);
        when(policy.getResumeHours()).thenReturn(24);
        when(settings.get()).thenReturn(policy);
        service = new ListeningSessionCommandService(sessions, attempts, responses, sets,
                new ListeningTaskSelectionPolicy(), new ListeningIdempotencyPolicy(),
                settings, mock(LanguageLearningUserSettingQueryService.class),
                new LanguageLearningJsonCodec(new ObjectMapper()), locks, mapper);
    }

    @Test
    void pollingAttachesNewlyReadyItemsOnceWithoutReplacingPreviousAnswersOrTouchingActivity() {
        ListeningItem first = item(1, true);
        ListeningItem second = item(2, true);
        ListeningItem third = item(3, false);
        ListeningItemAttempt previous = addAttempt(first, ListeningEvaluationPurpose.OFFICIAL);
        previous.skip(LocalDateTime.now());
        activeItems.addAll(List.of(first, second, third));
        LocalDateTime activity = session.getLastActivityAt();

        assertThat(service.expireIfNeeded(10L, 30L)).isFalse();
        assertThat(service.expireIfNeeded(10L, 30L)).isFalse();

        assertThat(storedAttempts).hasSize(2);
        assertThat(storedAttempts.getFirst()).isSameAs(previous);
        assertThat(previous.isFinalized()).isTrue();
        assertThat(session.getStatus()).isEqualTo(ListeningSessionStatus.IN_PROGRESS);
        assertThat(session.getLastActivityAt()).isEqualTo(activity);
        assertThat(session.getSelectionSnapshotJson()).isEqualTo("[1,2]");
        verify(attempts, times(1)).saveAndFlush(any(ListeningItemAttempt.class));
        verify(responses, times(ListeningTaskType.values().length)).save(any());
    }

    @Test
    void responseMappingHappensAfterReconciliationWithinReadCommittedTransaction() throws Exception {
        activeItems.add(item(1, true));
        when(mapper.session(session)).thenAnswer(invocation -> {
            assertThat(storedAttempts).hasSize(1);
            return null;
        });

        service.synchronizeAndView(10L, 30L);

        verify(mapper).session(session);
        Transactional transaction = ListeningSessionCommandService.class
                .getMethod("synchronizeAndView", Long.class, Long.class)
                .getAnnotation(Transactional.class);
        assertThat(transaction.readOnly()).isFalse();
        assertThat(transaction.isolation()).isEqualTo(Isolation.READ_COMMITTED);
    }

    @Test
    void laterPollingHealsAnItemWhichWasNotReadyDuringSessionSnapshot() {
        ListeningItem first = item(1, true);
        ListeningItem second = item(2, false);
        addAttempt(first, ListeningEvaluationPurpose.OFFICIAL);
        activeItems.addAll(List.of(first, second));
        service.expireIfNeeded(10L, 30L);
        assertThat(storedAttempts).hasSize(1);

        when(second.isPlayable(any(LocalDateTime.class))).thenReturn(true);
        service.expireIfNeeded(10L, 30L);
        assertThat(storedAttempts).hasSize(2);
    }

    @Test
    void replacementForAlreadyAttachedSlotDoesNotCreateAnotherOfficialAttempt() {
        addAttempt(item(1, true), ListeningEvaluationPurpose.OFFICIAL);
        ListeningItem replacement = item(1, true);
        when(replacement.getId()).thenReturn(101L);
        activeItems.add(replacement);

        service.resume(10L, 30L);

        assertThat(storedAttempts).hasSize(1);
        verify(attempts, never()).saveAndFlush(any());
    }

    @Test
    void completeCannotTreatOneOfficialAndFourPracticeAttemptsAsFiveItems() {
        addAttempt(item(1, true), ListeningEvaluationPurpose.OFFICIAL).skip(LocalDateTime.now());
        for (int index = 2; index <= 5; index++) {
            addAttempt(item(index, true), ListeningEvaluationPurpose.PRACTICE).skip(LocalDateTime.now());
        }

        assertThatThrownBy(() -> service.complete(10L, 30L)).isInstanceOf(BusinessException.class);
        assertThat(session.getStatus()).isEqualTo(ListeningSessionStatus.IN_PROGRESS);
    }

    @Test
    void completeRequiresEveryOfficialSlotToBeTerminal() {
        for (int index = 1; index <= 5; index++) {
            ListeningItemAttempt attempt = addAttempt(item(index, true), ListeningEvaluationPurpose.OFFICIAL);
            if (index < 5) {
                attempt.skip(LocalDateTime.now());
            }
        }
        assertThatThrownBy(() -> service.complete(10L, 30L)).isInstanceOf(BusinessException.class);
        storedAttempts.getLast().skip(LocalDateTime.now());

        assertThat(service.complete(10L, 30L).expired()).isFalse();
        assertThat(session.getStatus()).isEqualTo(ListeningSessionStatus.COMPLETED);
    }

    @Test
    void duplicateOrOutOfRangeIndicesCannotSatisfyTarget() {
        assertThat(session.hasAllTargetItems(List.of(1, 1, 2, 3, 4))).isFalse();
        assertThat(session.hasAllTargetItems(List.of(2, 3, 4, 5, 6))).isFalse();
        assertThat(session.hasAllTargetItems(List.of(1, 2, 3, 4, 5))).isTrue();
    }

    @Test
    void newSessionCannotSplitOfficialSlotsOwnedByAnEarlierSession() {
        when(locks.ownedDailySet(10L, 20L)).thenReturn(dailySet);
        when(dailySet.getLearningMode()).thenReturn(ListeningLearningMode.SUMMARY);
        when(dailySet.isUsable()).thenReturn(true);
        when(attempts.existsByItemDailySetIdAndEvaluationPurpose(20L, ListeningEvaluationPurpose.OFFICIAL))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(10L,
                new ListeningApiContract.SessionCreateRequest(20L, List.of(ListeningTaskType.SUMMARY), "fresh-key")))
                .isInstanceOf(BusinessException.class);
        verify(sessions, never()).saveAndFlush(any());
        verify(attempts, never()).saveAndFlush(any());
    }

    private ListeningItem item(int index, boolean ready) {
        ListeningItem item = mock(ListeningItem.class);
        when(item.getId()).thenReturn((long) index);
        when(item.getItemIndex()).thenReturn(index);
        when(item.getDailySet()).thenReturn(dailySet);
        when(item.isPlayable(any(LocalDateTime.class))).thenReturn(ready);
        return item;
    }

    private ListeningItemAttempt addAttempt(ListeningItem item, ListeningEvaluationPurpose purpose) {
        ListeningItemAttempt attempt = ListeningItemAttempt.create(session, item, 1,
                purpose, "item:" + item.getId(), LocalDateTime.now());
        storedAttempts.add(attempt);
        return attempt;
    }
}
