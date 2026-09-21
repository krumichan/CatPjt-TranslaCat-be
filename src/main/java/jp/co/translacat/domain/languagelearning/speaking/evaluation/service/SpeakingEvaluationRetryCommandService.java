package jp.co.translacat.domain.languagelearning.speaking.evaluation.service;

import jp.co.translacat.domain.languagelearning.activity.repository.LearningActivityRepository;
import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingEvaluationStatus;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.service.SpeakingEvaluationJobQueueService;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.repository.SpeakingEvaluationJobRepository;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.entity.SpeakingEvaluationJob;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingResultKind;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionQueryService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpeakingEvaluationRetryCommandService {
    private final SpeakingSessionQueryService sessionQueryService;
    private final SpeakingEvaluationJobQueueService queueService;
    private final LearningActivityRepository activityRepository;
    private final SpeakingEvaluationJobRepository jobRepository;

    @Transactional
    public void retry(Long userId, Long sessionId) {
        var session = sessionQueryService.getOwnedEntityForUpdate(userId, sessionId);
        if (session.getResultKind() == SpeakingResultKind.SESSION_COACHING) {
            var job = jobRepository.findOneBySessionIdAndProblemIndex(sessionId, 0)
                    .orElseThrow(() -> new BusinessException(
                            "Speaking coaching 작업을 찾을 수 없습니다.",
                            LanguageLearningErrorCode.SPEAKING_EVALUATION_FAILED));
            if (job.getStatus() == SpeakingEvaluationJob.Status.PENDING
                    || job.getStatus() == SpeakingEvaluationJob.Status.RUNNING) return;
            queueService.retry(session, 0);
            return;
        }
        // Duplicate clicks after an accepted retry are idempotent, not new attempts.
        if (session.getEvaluationStatus() == SpeakingEvaluationStatus.PENDING
                || session.getEvaluationStatus() == SpeakingEvaluationStatus.EVALUATING) return;
        if (session.isActive() || session.getEvaluationStatus() != SpeakingEvaluationStatus.FAILED)
            throw new BusinessException("Speaking 평가를 재시도할 수 없는 상태입니다.",
                    LanguageLearningErrorCode.SPEAKING_EVALUATION_FAILED);
        queueService.retry(session, 0);
        session.markEvaluationPending();
        activityRepository.findBySourceAndReferenceId(LearningSource.SPEAKING, String.valueOf(sessionId))
                .orElseThrow().markEvaluating();
    }
}
