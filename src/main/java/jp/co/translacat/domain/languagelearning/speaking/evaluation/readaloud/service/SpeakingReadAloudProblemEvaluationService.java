package jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.service;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingPracticeMode;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.dto.SpeakingReadAloudProblemEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.entity.SpeakingReadAloudProblemEvaluation;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.service.SpeakingEvaluationJobQueueService;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionPolicySnapshotService;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.repository.SpeakingReadAloudProblemEvaluationRepository;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.policy.SpeakingSessionPolicy;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionCompletionCommandService;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionLifecycleService;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionQueryService;
import jp.co.translacat.domain.languagelearning.speaking.turn.entity.SpeakingTurn;
import jp.co.translacat.domain.languagelearning.speaking.turn.repository.SpeakingTurnRepository;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SpeakingReadAloudProblemEvaluationService {

    private final SpeakingSessionQueryService sessionQueryService;
    private final SpeakingSessionLifecycleService lifecycleService;
    private final SpeakingSessionCompletionCommandService completionCommandService;
    private final SpeakingTurnRepository turnRepository;
    private final SpeakingReadAloudProblemEvaluationRepository evaluationRepository;
    private final SpeakingEvaluationJobQueueService queueService;
    private final SpeakingSessionPolicySnapshotService snapshotService;

    @Transactional
    public SpeakingReadAloudProblemEvaluationResponseDto submit(
            Long userId,
            Long sessionId,
            int problemIndex
    ) {
        SpeakingSession session = sessionQueryService.getOwnedEntityForUpdate(
                userId,
                sessionId
        );
        requireReadAloud(session);
        validateProblemIndex(problemIndex);
        // A replay of the final submit remains valid after the session is completed.
        var submitted = evaluationRepository.findBySessionIdAndProblemIndex(sessionId, problemIndex);
        if (submitted.isPresent()) return SpeakingReadAloudProblemEvaluationResponseDto.from(submitted.get());
        lifecycleService.expireIfNeeded(session);
        lifecycleService.requireActive(session);
        validateSubmissionOrder(sessionId, problemIndex);

        List<SpeakingTurn> attempts = turnRepository
                .findAllBySessionIdAndProblemIndexOrderByAttemptIndexAsc(
                        sessionId,
                        problemIndex
                );
        validateAttempts(attempts);
        if (problemIndex < SpeakingSessionPolicy.READ_ALOUD_DAILY_ITEM_COUNT) {
            requireNextProblemPrompt(attempts);
        }

        int includedAttempts = (int) attempts.stream()
                .filter(turn -> !turn.isExcludedFromEvaluation())
                .count();
        var snapshot = snapshotService.read(session);
        SpeakingReadAloudProblemEvaluation evaluation = SpeakingReadAloudProblemEvaluation.pending(
                session, problemIndex, includedAttempts);
        evaluation.configureRetryLimit(snapshot.manualRetryLimitPerStage());
        if (!snapshot.speakingEvaluationEnabled()) evaluation.markSkipped();
        evaluationRepository.save(evaluation);
        if (snapshot.speakingEvaluationEnabled()) queueService.enqueue(session, problemIndex);

        if (problemIndex == SpeakingSessionPolicy.READ_ALOUD_DAILY_ITEM_COUNT
                && allFiveProblemsSubmitted(sessionId)) {
            completionCommandService.complete(userId, sessionId, false);
        }

        return SpeakingReadAloudProblemEvaluationResponseDto.from(evaluation);
    }

    /** Only failed submitted evaluations may retry after completion; recording is never reopened. */
    @Transactional
    public SpeakingReadAloudProblemEvaluationResponseDto retry(Long userId, Long sessionId, int problemIndex) {
        SpeakingSession session = sessionQueryService.getOwnedEntityForUpdate(userId, sessionId);
        requireReadAloud(session);
        validateProblemIndex(problemIndex);
        var evaluation = evaluationRepository.findBySessionIdAndProblemIndex(sessionId, problemIndex)
                .orElseThrow(() -> invalid("제출한 듣고 리피트 평가가 없습니다."));
        if ("PENDING".equals(evaluation.getStatus()) || "EVALUATING".equals(evaluation.getStatus()))
            return SpeakingReadAloudProblemEvaluationResponseDto.from(evaluation);
        if (!"FAILED".equals(evaluation.getStatus())) throw invalid("실패한 문제 평가만 재시도할 수 있습니다.");
        int retryCount = queueService.retry(session, problemIndex);
        evaluation.acceptRetry(retryCount);
        return SpeakingReadAloudProblemEvaluationResponseDto.from(evaluation);
    }

    @Transactional(readOnly = true)
    public List<SpeakingReadAloudProblemEvaluationResponseDto> list(
            Long userId,
            Long sessionId
    ) {
        SpeakingSession session = sessionQueryService.getOwnedEntity(userId, sessionId);
        if (session.getPracticeMode() != SpeakingPracticeMode.READ_ALOUD) {
            return List.of();
        }
        return evaluationRepository
                .findAllBySessionIdOrderByProblemIndexAsc(sessionId)
                .stream()
                .map(SpeakingReadAloudProblemEvaluationResponseDto::from)
                .toList();
    }

    private void validateAttempts(List<SpeakingTurn> attempts) {
        List<SpeakingTurn> included = attempts.stream()
                .filter(turn -> !turn.isExcludedFromEvaluation())
                .toList();
        if (included.size() < SpeakingSessionPolicy.READ_ALOUD_REQUIRED_ATTEMPTS_PER_ITEM
                || included.size() > SpeakingSessionPolicy.READ_ALOUD_MAX_ATTEMPTS_PER_ITEM) {
            throw invalid("듣고 리피트 문제는 2회 이상, 최대 3회의 발화가 필요합니다.");
        }
        long validStt = included.stream()
                .filter(turn -> turn.getTranscript() != null)
                .filter(turn -> !turn.getTranscript().isBlank())
                .count();
        double ratio = included.isEmpty()
                ? 0.0
                : (double) validStt / included.size();
        if (ratio < 0.80) {
            throw invalid("듣고 리피트 문제의 유효 STT 비율이 80% 미만입니다.");
        }
    }

    private void validateSubmissionOrder(Long sessionId, int problemIndex) {
        long submittedBefore = evaluationRepository
                .findAllBySessionIdOrderByProblemIndexAsc(sessionId)
                .stream()
                .filter(item -> item.getProblemIndex() < problemIndex)
                .count();
        if (submittedBefore != problemIndex - 1L) {
            throw invalid("이전 듣고 리피트 문제를 먼저 평가 요청해야 합니다.");
        }
    }

    private void requireNextProblemPrompt(List<SpeakingTurn> attempts) {
        boolean prepared = attempts.stream()
                .anyMatch(turn -> turn.getAssistantText() != null
                        && !turn.getAssistantText().isBlank());
        if (!prepared) {
            throw invalid("다음 듣고 리피트 문제가 아직 준비되지 않았습니다.");
        }
    }

    private boolean allFiveProblemsSubmitted(Long sessionId) {
        return evaluationRepository
                .findAllBySessionIdOrderByProblemIndexAsc(sessionId)
                .stream()
                .map(SpeakingReadAloudProblemEvaluation::getProblemIndex)
                .distinct()
                .count() == SpeakingSessionPolicy.READ_ALOUD_DAILY_ITEM_COUNT;
    }

    private void requireReadAloud(SpeakingSession session) {
        if (session.getPracticeMode() != SpeakingPracticeMode.READ_ALOUD) {
            throw invalid("듣고 리피트 문제 평가 API는 READ_ALOUD에서만 사용할 수 있습니다.");
        }
    }

    private void validateProblemIndex(int problemIndex) {
        if (problemIndex < 1
                || problemIndex > SpeakingSessionPolicy.READ_ALOUD_DAILY_ITEM_COUNT) {
            throw invalid("듣고 리피트 문제 번호가 유효하지 않습니다.");
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(
                message,
                LanguageLearningErrorCode.SPEAKING_EVALUATION_FAILED
        );
    }
}
