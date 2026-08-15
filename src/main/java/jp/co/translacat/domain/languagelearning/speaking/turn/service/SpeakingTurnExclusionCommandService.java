package jp.co.translacat.domain.languagelearning.speaking.turn.service;

import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionQueryService;
import jp.co.translacat.domain.languagelearning.speaking.turn.entity.SpeakingTurn;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpeakingTurnExclusionCommandService {

    private final SpeakingSessionQueryService sessionQueryService;
    private final SpeakingTurnQueryService turnQueryService;

    @Transactional
    public SpeakingTurn exclude(
            Long userId,
            Long sessionId,
            Long turnId
    ) {
        SpeakingSession session = sessionQueryService.getOwnedEntity(
                userId,
                sessionId
        );
        if (session.getEvaluationStatus()
                == jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingEvaluationStatus.EVALUATED) {
            throw new jp.co.translacat.global.exception.BusinessException(
                    "평가 완료 후에는 Turn을 제외할 수 없습니다.",
                    jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode.SESSION_NOT_ACTIVE
            );
        }
        SpeakingTurn turn = turnQueryService.getOwnedEntity(
                userId,
                sessionId,
                turnId
        );
        turn.exclude();
        return turn;
    }
}
