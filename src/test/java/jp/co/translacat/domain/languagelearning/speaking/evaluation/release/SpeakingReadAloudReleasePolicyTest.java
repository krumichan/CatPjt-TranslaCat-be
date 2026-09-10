package jp.co.translacat.domain.languagelearning.speaking.evaluation.release;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingPracticeMode;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.service.SpeakingEvaluationJobQueueService;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.entity.SpeakingReadAloudProblemEvaluation;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.repository.SpeakingReadAloudProblemEvaluationRepository;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.service.SpeakingReadAloudProblemEvaluationService;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.service.*;
import jp.co.translacat.domain.languagelearning.speaking.turn.entity.SpeakingTurn;
import jp.co.translacat.domain.languagelearning.speaking.turn.repository.SpeakingTurnRepository;
import jp.co.translacat.domain.languagelearning.speaking.turn.service.*;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static jp.co.translacat.domain.languagelearning.speaking.evaluation.release.SpeakingReleaseFixtures.policy;

@ExtendWith(MockitoExtension.class)
class SpeakingReadAloudReleasePolicyTest {
    @Mock SpeakingSessionQueryService sessions;
    @Mock SpeakingSessionLifecycleService lifecycle;
    @Mock SpeakingSessionCompletionCommandService completion;
    @Mock SpeakingTurnRepository turns;
    @Mock SpeakingReadAloudProblemEvaluationRepository problems;
    @Mock SpeakingEvaluationJobQueueService queue;
    @Mock SpeakingSessionPolicySnapshotService snapshots;
    @Mock SpeakingTurnQueryService turnQuery;
    @InjectMocks SpeakingReadAloudProblemEvaluationService service;
    @InjectMocks SpeakingTurnExclusionCommandService exclusion;

    @Test void disabledEvaluationStillSubmitsTheProblemButDoesNotQueueAiWork() {
        var session = mock(SpeakingSession.class);
        when(sessions.getOwnedEntityForUpdate(1L, 2L)).thenReturn(session);
        when(session.getPracticeMode()).thenReturn(SpeakingPracticeMode.READ_ALOUD);
        var first = mock(SpeakingTurn.class);
        var second = mock(SpeakingTurn.class);
        when(first.getTranscript()).thenReturn("読みます。");
        when(second.getTranscript()).thenReturn("読みます。");
        when(first.getAssistantText()).thenReturn("次の問題。");
        when(turns.findAllBySessionIdAndProblemIndexOrderByAttemptIndexAsc(2L, 1)).thenReturn(List.of(first, second));
        when(snapshots.read(session)).thenReturn(policy(false));
        var result = service.submit(1L, 2L, 1);
        assertThat(result.status()).isEqualTo("NOT_REQUESTED");
        assertThat(result.overallScore()).isNull();
        assertThat(result.attemptCount()).isEqualTo(2);
        verify(problems).save(any(SpeakingReadAloudProblemEvaluation.class));
        verifyNoInteractions(queue, completion);
    }
    @Test void replayOfTheFinalSubmissionDoesNotReopenOrRequeueTheSession() {
        var session = mock(SpeakingSession.class);
        when(sessions.getOwnedEntityForUpdate(1L, 2L)).thenReturn(session);
        when(session.getPracticeMode()).thenReturn(SpeakingPracticeMode.READ_ALOUD);
        var submitted = SpeakingReadAloudProblemEvaluation.pending(session, 5, 2);
        submitted.markFailed("failed");
        when(problems.findBySessionIdAndProblemIndex(2L, 5)).thenReturn(Optional.of(submitted));
        assertThat(service.submit(1L, 2L, 5).status()).isEqualTo("FAILED");
        verifyNoInteractions(lifecycle, queue, turns, completion, snapshots);
    }
    @Test void completedSessionCannotChangeExcludedEvidence() {
        var session = mock(SpeakingSession.class);
        when(sessions.getOwnedEntityForUpdate(1L, 2L)).thenReturn(session);
        assertThatThrownBy(() -> exclusion.exclude(1L, 2L, 3L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(turnQuery, problems);
    }
    @Test void submittedProblemEvidenceCannotBeExcludedEvenWhileNextProblemIsActive() {
        var session = mock(SpeakingSession.class);
        var turn = mock(SpeakingTurn.class);
        when(sessions.getOwnedEntityForUpdate(1L, 2L)).thenReturn(session);
        when(session.isActive()).thenReturn(true);
        when(session.getPracticeMode()).thenReturn(SpeakingPracticeMode.READ_ALOUD);
        when(turnQuery.getOwnedEntity(1L, 2L, 3L)).thenReturn(turn);
        when(turn.getProblemIndex()).thenReturn(1);
        var submitted = SpeakingReadAloudProblemEvaluation.pending(session, 1, 2);
        when(problems.findBySessionIdAndProblemIndex(2L, 1)).thenReturn(Optional.of(submitted));
        assertThatThrownBy(() -> exclusion.exclude(1L, 2L, 3L)).isInstanceOf(BusinessException.class);
        verify(turn, never()).exclude();
    }
}
