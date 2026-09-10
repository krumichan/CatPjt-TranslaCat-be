package jp.co.translacat.domain.languagelearning.speaking.evaluation.service;

import jp.co.translacat.domain.languagelearning.activity.repository.LearningActivityRepository;
import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingEvaluationStatus;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.job.service.SpeakingEvaluationJobQueueService;
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

    @Transactional
    public void retry(Long userId, Long sessionId) {
        var session = sessionQueryService.getOwnedEntityForUpdate(userId, sessionId);
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
