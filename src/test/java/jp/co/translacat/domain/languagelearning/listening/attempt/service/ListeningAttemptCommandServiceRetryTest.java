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
import jp.co.translacat.domain.languagelearning.listening.session.repository.ListeningSessionRepository;
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
        ListeningSessionRepository sessionRepository = mock(ListeningSessionRepository.class);
        ListeningPolicySettingQueryService policySettingService = mock(ListeningPolicySettingQueryService.class);
        ListeningOutboxCommandService outboxCommandService = mock(ListeningOutboxCommandService.class);
        ListeningViewMapper viewMapper = mock(ListeningViewMapper.class);

        ListeningAttemptCommandService service = new ListeningAttemptCommandService(
                attemptRepository,
                responseRepository,
                sessionRepository,
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
                java.util.List.of()
        );
        when(attemptRepository.findLockedById(attemptId)).thenReturn(Optional.of(attempt));
        when(sessionRepository.findLockedById(sessionId)).thenReturn(Optional.of(session));
        when(responseRepository.findByAttemptIdAndTaskType(attemptId, ListeningTaskType.SUMMARY))
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
}
