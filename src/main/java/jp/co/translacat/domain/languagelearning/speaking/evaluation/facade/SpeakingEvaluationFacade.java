package jp.co.translacat.domain.languagelearning.speaking.evaluation.facade;

import jp.co.translacat.domain.languagelearning.speaking.evaluation.dto.response.SpeakingEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.service.SpeakingEvaluationQueryService;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingEvaluationStatus;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionQueryService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.service.SpeakingEvaluationRetryCommandService;

@Service
@RequiredArgsConstructor
public class SpeakingEvaluationFacade {

    private final SpeakingSessionQueryService sessionQueryService;
    private final SpeakingEvaluationQueryService evaluationQueryService;
    private final SpeakingEvaluationRetryCommandService retryCommandService;

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
        retryCommandService.retry(userId, sessionId);
    }
}
