package jp.co.translacat.domain.languagelearning.listening.attempt.service;

import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.listening.attempt.entity.ListeningItemAttempt;
import jp.co.translacat.domain.languagelearning.listening.attempt.repository.ListeningItemAttemptRepository;
import jp.co.translacat.domain.languagelearning.listening.audio.port.ListeningAudioStoragePort;
import jp.co.translacat.domain.languagelearning.listening.audio.service.ListeningAudioKeyFactory;
import jp.co.translacat.domain.languagelearning.listening.audio.validator.ListeningAudioValidator;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningOutboxType;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningSessionStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.listening.dto.ListeningApiContract;
import jp.co.translacat.domain.languagelearning.listening.evaluation.service.ListeningAttemptFinalizationCommandService;
import jp.co.translacat.domain.languagelearning.listening.outbox.service.ListeningOutboxCommandService;
import jp.co.translacat.domain.languagelearning.listening.policy.ListeningIdempotencyPolicy;
import jp.co.translacat.domain.languagelearning.listening.policy.ListeningTaskSelectionPolicy;
import jp.co.translacat.domain.languagelearning.listening.response.entity.ListeningTaskResponse;
import jp.co.translacat.domain.languagelearning.listening.response.repository.ListeningTaskResponseRepository;
import jp.co.translacat.domain.languagelearning.listening.service.ListeningViewMapper;
import jp.co.translacat.domain.languagelearning.listening.session.entity.ListeningSession;
import jp.co.translacat.domain.languagelearning.listening.session.service.ListeningSessionLockService;
import jp.co.translacat.domain.languagelearning.listening.setting.entity.ListeningPolicySetting;
import jp.co.translacat.domain.languagelearning.listening.setting.service.ListeningPolicySettingQueryService;
import jp.co.translacat.domain.user.entity.User;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListeningAttemptCommandServiceRetryTest {

    @Test
    void retryEvaluationReopensCompletedSessionAsBackgroundEvaluation() {
        ListeningItemAttemptRepository attemptRepository = mock(ListeningItemAttemptRepository.class);
        ListeningTaskResponseRepository responseRepository = mock(ListeningTaskResponseRepository.class);
        ListeningSessionLockService lockService = mock(ListeningSessionLockService.class);
        ListeningPolicySettingQueryService policySettingService = mock(ListeningPolicySettingQueryService.class);
        ListeningOutboxCommandService outboxCommandService = mock(ListeningOutboxCommandService.class);
        ListeningViewMapper viewMapper = mock(ListeningViewMapper.class);

        ListeningAttemptCommandService service = new ListeningAttemptCommandService(
                attemptRepository,
                responseRepository,
                lockService,
                policySettingService,
                mock(ListeningTaskSelectionPolicy.class),
                mock(ListeningIdempotencyPolicy.class),
                outboxCommandService,
                mock(ListeningAttemptFinalizationCommandService.class),
                mock(ListeningAudioStoragePort.class),
                mock(ListeningAudioValidator.class),
                mock(ListeningAudioKeyFactory.class),
                viewMapper,
                mock(LanguageLearningJsonCodec.class)
        );

        long userId = 90001L;
        long attemptId = 90006L;
        long sessionId = 90002L;
        long responseId = 90021L;

        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);

        ListeningSession session = mock(ListeningSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.getUser()).thenReturn(user);
        when(session.getStatus()).thenReturn(ListeningSessionStatus.COMPLETED);

        ListeningItemAttempt attempt = mock(ListeningItemAttempt.class);
        when(attempt.getSession()).thenReturn(session);

        ListeningTaskResponse response = mock(ListeningTaskResponse.class);
        when(response.getId()).thenReturn(responseId);
        when(response.getStatus()).thenReturn(ListeningTaskStatus.EVALUATION_FAILED);
        when(response.getManualRetryCount()).thenReturn(0, 1);

        ListeningPolicySetting setting = mock(ListeningPolicySetting.class);
        when(setting.getManualRetryLimit()).thenReturn(1);

        ListeningApiContract.AttemptView expected = new ListeningApiContract.AttemptView(
                attemptId,
                30006L,
                1,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                0,
                0.0,
                null,
                java.util.List.of(),
                1
        );
        when(lockService.ownedAttempt(userId, attemptId)).thenReturn(attempt);
        when(responseRepository.findLockedByAttemptIdAndTaskType(attemptId, ListeningTaskType.SUMMARY))
                .thenReturn(Optional.of(response));
        when(policySettingService.get()).thenReturn(setting);
        when(viewMapper.attempt(attempt)).thenReturn(expected);

        ListeningApiContract.AttemptView actual = service.retryEvaluation(
                userId,
                attemptId,
                new ListeningApiContract.RetryRequest(
                        ListeningTaskType.SUMMARY,
                        "summary-retry-90006"
                )
        );

        assertThat(actual).isSameAs(expected);
        verify(response).prepareManualRetry(1);
        verify(attempt).registerManualEvaluationRetry(1);
        verify(attempt).markEvaluating();
        verify(response).markEvaluating();
        verify(session).resumeEvaluationForRetry(any(LocalDateTime.class));
        verify(outboxCommandService).enqueue(
                eq(ListeningOutboxType.EVALUATE_TASK),
                eq(responseId),
                eq(null),
                eq("listening:response:90021:evaluation:1")
        );
    }
    @Test
    void retryFailedEvaluationsRetriesEveryRetryableOfficialTaskInOneRequest() {
        ListeningItemAttemptRepository attemptRepository = mock(ListeningItemAttemptRepository.class);
        ListeningTaskResponseRepository responseRepository = mock(ListeningTaskResponseRepository.class);
        ListeningSessionLockService lockService = mock(ListeningSessionLockService.class);
        ListeningPolicySettingQueryService policySettingService = mock(ListeningPolicySettingQueryService.class);
        ListeningOutboxCommandService outboxCommandService = mock(ListeningOutboxCommandService.class);

        ListeningAttemptCommandService service = new ListeningAttemptCommandService(
                attemptRepository,
                responseRepository,
                lockService,
                policySettingService,
                mock(ListeningTaskSelectionPolicy.class),
                mock(ListeningIdempotencyPolicy.class),
                outboxCommandService,
                mock(ListeningAttemptFinalizationCommandService.class),
                mock(ListeningAudioStoragePort.class),
                mock(ListeningAudioValidator.class),
                mock(ListeningAudioKeyFactory.class),
                mock(ListeningViewMapper.class),
                mock(LanguageLearningJsonCodec.class)
        );

        long userId = 91001L;
        long sessionId = 91002L;
        long retryableAttemptId = 91006L;
        long exhaustedAttemptId = 91007L;

        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);

        ListeningSession session = mock(ListeningSession.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.getUser()).thenReturn(user);
        when(session.getStatus()).thenReturn(ListeningSessionStatus.COMPLETED);

        ListeningItemAttempt retryableAttempt = mock(ListeningItemAttempt.class);
        when(retryableAttempt.getId()).thenReturn(retryableAttemptId);
        when(retryableAttempt.getSession()).thenReturn(session);
        when(retryableAttempt.isOfficial()).thenReturn(true);

        ListeningItemAttempt exhaustedAttempt = mock(ListeningItemAttempt.class);
        when(exhaustedAttempt.getId()).thenReturn(exhaustedAttemptId);
        when(exhaustedAttempt.getSession()).thenReturn(session);
        when(exhaustedAttempt.isOfficial()).thenReturn(true);

        ListeningTaskResponse retryableResponse = mock(ListeningTaskResponse.class);
        when(retryableResponse.getId()).thenReturn(91021L);
        when(retryableResponse.getStatus()).thenReturn(ListeningTaskStatus.EVALUATION_FAILED);
        when(retryableResponse.getManualRetryCount()).thenReturn(0, 1);

        ListeningTaskResponse exhaustedResponse = mock(ListeningTaskResponse.class);
        when(exhaustedResponse.getId()).thenReturn(91022L);
        when(exhaustedResponse.getStatus()).thenReturn(ListeningTaskStatus.EVALUATION_FAILED);
        when(exhaustedResponse.getManualRetryCount()).thenReturn(1);

        ListeningPolicySetting setting = mock(ListeningPolicySetting.class);
        when(setting.getManualRetryLimit()).thenReturn(1);

        when(lockService.ownedSession(userId, sessionId)).thenReturn(session);
        when(attemptRepository.findAllLockedBySessionIdOrderByItemItemIndexAscAttemptNoAsc(sessionId))
                .thenReturn(java.util.List.of(retryableAttempt, exhaustedAttempt));
        when(lockService.ownedAttempt(userId, retryableAttemptId)).thenReturn(retryableAttempt);
        when(lockService.ownedAttempt(userId, exhaustedAttemptId)).thenReturn(exhaustedAttempt);
        when(responseRepository.findAllLockedByAttemptIdOrderByTaskTypeAsc(retryableAttemptId))
                .thenReturn(java.util.List.of(retryableResponse));
        when(responseRepository.findAllLockedByAttemptIdOrderByTaskTypeAsc(exhaustedAttemptId))
                .thenReturn(java.util.List.of(exhaustedResponse));
        when(policySettingService.get()).thenReturn(setting);

        ListeningApiContract.BulkRetryView actual = service.retryFailedEvaluations(
                userId,
                sessionId
        );

        assertThat(actual.failedTaskCount()).isEqualTo(2);
        assertThat(actual.retriedTaskCount()).isEqualTo(1);
        assertThat(actual.exhaustedTaskCount()).isEqualTo(1);
        verify(retryableResponse).prepareManualRetry(1);
        verify(retryableAttempt).registerManualEvaluationRetry(1);
        verify(retryableAttempt).markEvaluating();
        verify(retryableResponse).markEvaluating();
        verify(session).resumeEvaluationForRetry(any(LocalDateTime.class));
        verify(outboxCommandService).enqueue(
                eq(ListeningOutboxType.EVALUATE_TASK),
                eq(91021L),
                eq(null),
                eq("listening:response:91021:evaluation:1")
        );
    }

}
