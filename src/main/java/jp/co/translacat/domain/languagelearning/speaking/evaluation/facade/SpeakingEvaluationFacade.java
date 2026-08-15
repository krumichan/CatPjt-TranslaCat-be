package jp.co.translacat.domain.languagelearning.speaking.evaluation.facade;

import jp.co.translacat.domain.languagelearning.speaking.evaluation.dto.response.SpeakingEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.event.SpeakingEvaluationRequestedEvent;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.service.SpeakingEvaluationQueryService;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingEvaluationStatus;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.model.SpeakingSessionPolicySnapshot;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionPolicySnapshotService;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionQueryService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SpeakingEvaluationFacade {

    private final SpeakingSessionQueryService sessionQueryService;
    private final SpeakingEvaluationQueryService evaluationQueryService;
    private final SpeakingSessionPolicySnapshotService policySnapshotService;
    private final ApplicationEventPublisher eventPublisher;

    public SpeakingEvaluationResponseDto get(
            Long userId,
            Long sessionId
    ) {
        SpeakingSession session = sessionQueryService.getOwnedEntity(
                userId,
                sessionId
        );
        SpeakingEvaluationResponseDto response =
                evaluationQueryService.getResponse(session.getId());
        if (response == null
                && session.getEvaluationStatus()
                == SpeakingEvaluationStatus.PENDING) {
            throw new BusinessException(
                    "Speaking 평가가 진행 중입니다.",
                    LanguageLearningErrorCode.EVALUATION_PENDING
            );
        }
        return response;
    }

    public void retry(Long userId, Long sessionId) {
        SpeakingSession session = sessionQueryService.getOwnedEntity(
                userId,
                sessionId
        );
        SpeakingSessionPolicySnapshot snapshot = policySnapshotService.read(session);
        if (!snapshot.speakingEvaluationEnabled()) {
            throw new BusinessException(
                    "Speaking 평가가 비활성화되어 있습니다.",
                    LanguageLearningErrorCode.SPEAKING_DISABLED
            );
        }
        if (session.isActive()) {
            throw new BusinessException(
                    "진행 중인 Session은 평가할 수 없습니다.",
                    LanguageLearningErrorCode.SESSION_NOT_ACTIVE
            );
        }
        if (session.getEvaluationStatus() != SpeakingEvaluationStatus.FAILED
                || snapshot.manualRetryLimitPerStage() < 1) {
            throw new BusinessException(
                    "Speaking 평가를 수동 재시도할 수 없는 상태입니다.",
                    LanguageLearningErrorCode.SPEAKING_EVALUATION_FAILED
            );
        }
        eventPublisher.publishEvent(
                new SpeakingEvaluationRequestedEvent(sessionId, 1)
        );
    }
}
