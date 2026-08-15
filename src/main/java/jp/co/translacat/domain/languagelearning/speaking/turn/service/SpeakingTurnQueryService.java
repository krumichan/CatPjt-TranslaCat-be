package jp.co.translacat.domain.languagelearning.speaking.turn.service;

import jp.co.translacat.domain.languagelearning.speaking.turn.dto.response.SpeakingTurnResponseDto;
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
@Transactional(readOnly = true)
public class SpeakingTurnQueryService {

    private final SpeakingTurnRepository turnRepository;

    public SpeakingTurn getOwnedEntity(
            Long userId,
            Long sessionId,
            Long turnId
    ) {
        return turnRepository
                .findByIdAndSessionIdAndSessionUserId(
                        turnId,
                        sessionId,
                        userId
                )
                .orElseThrow(() -> new BusinessException(
                        "Speaking Turn을 찾을 수 없습니다.",
                        LanguageLearningErrorCode.TURN_NOT_FOUND
                ));
    }

    public SpeakingTurn getOwnedEntityForUpdate(
            Long userId,
            Long sessionId,
            Long turnId
    ) {
        return turnRepository.findOneByIdAndSessionIdAndSessionUserId(
                turnId,
                sessionId,
                userId
        ).orElseThrow(() -> new BusinessException(
                "Speaking Turn을 찾을 수 없습니다.",
                LanguageLearningErrorCode.TURN_NOT_FOUND
        ));
    }

    public List<SpeakingTurn> getEntities(Long sessionId) {
        return turnRepository.findAllBySessionIdOrderByTurnIndexAsc(sessionId);
    }

    public List<SpeakingTurnResponseDto> getResponses(
            Long userId,
            Long sessionId
    ) {
        return getEntities(sessionId).stream()
                .filter(turn -> turn.getSession().getUser().getId().equals(userId))
                .map(this::toResponse)
                .toList();
    }

    public SpeakingTurnResponseDto toResponse(SpeakingTurn turn) {
        return new SpeakingTurnResponseDto(
                turn.getId(),
                turn.getTurnIndex(),
                turn.getStatus(),
                turn.getDurationSeconds(),
                turn.getTranscript(),
                turn.getSttConfidence(),
                turn.getAssistantText(),
                turn.getAssistantAudioObjectKey() == null
                        ? null
                        : "/api/v1/language-learning/speaking/sessions/"
                        + turn.getSession().getId()
                        + "/turns/" + turn.getId() + "/audio",
                turn.isExcludedFromEvaluation(),
                turn.getFailedStage(),
                turn.getErrorCode(),
                turn.getErrorMessage(),
                turn.getManualRetryCount(),
                turn.getCompletedAt()
        );
    }
}
