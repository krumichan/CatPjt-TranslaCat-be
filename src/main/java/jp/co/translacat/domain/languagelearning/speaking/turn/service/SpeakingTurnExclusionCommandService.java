package jp.co.translacat.domain.languagelearning.speaking.turn.service;

import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionQueryService;
import jp.co.translacat.domain.languagelearning.speaking.turn.entity.SpeakingTurn;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingPracticeMode;
import jp.co.translacat.domain.languagelearning.speaking.evaluation.readaloud.repository.SpeakingReadAloudProblemEvaluationRepository;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpeakingTurnExclusionCommandService {

    private final SpeakingSessionQueryService sessionQueryService;
    private final SpeakingTurnQueryService turnQueryService;
    private final SpeakingReadAloudProblemEvaluationRepository problemEvaluationRepository;

    @Transactional
    public SpeakingTurn exclude(
            Long userId,
            Long sessionId,
            Long turnId
    ) {
        SpeakingSession session = sessionQueryService.getOwnedEntityForUpdate(
                userId,
                sessionId
        );
        if (!session.isActive()) {
            throw new BusinessException("종료된 Session의 평가 발화를 변경할 수 없습니다.",
                    LanguageLearningErrorCode.SESSION_NOT_ACTIVE);
        }
        SpeakingTurn turn = turnQueryService.getOwnedEntity(
                userId,
                sessionId,
                turnId
        );
        if (session.getPracticeMode() == SpeakingPracticeMode.READ_ALOUD
                && turn.getProblemIndex() != null
                && problemEvaluationRepository.findBySessionIdAndProblemIndex(sessionId, turn.getProblemIndex()).isPresent()) {
            throw new BusinessException("제출한 문제의 평가 발화를 변경할 수 없습니다.",
                    LanguageLearningErrorCode.SESSION_NOT_ACTIVE);
        }
        turn.exclude();
        return turn;
    }
}
