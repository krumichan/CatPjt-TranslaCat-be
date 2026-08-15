package jp.co.translacat.domain.languagelearning.speaking.assistance.service;

import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingAssistanceResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.port.SpeakingAiClient;
import jp.co.translacat.domain.languagelearning.speaking.assistance.dto.request.SpeakingAssistanceRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.assistance.dto.response.SpeakingAssistanceResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.assistance.factory.SpeakingAssistanceAiRequestFactory;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.AssistanceType;
import jp.co.translacat.domain.languagelearning.speaking.session.entity.SpeakingSession;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionLifecycleService;
import jp.co.translacat.domain.languagelearning.speaking.session.service.SpeakingSessionQueryService;
import jp.co.translacat.domain.languagelearning.speaking.turn.entity.SpeakingTurn;
import jp.co.translacat.domain.languagelearning.speaking.turn.service.SpeakingTurnQueryService;
import jp.co.translacat.domain.languagelearning.speaking.usage.service.SpeakingAiUsageCommandService;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpeakingAssistanceService {

    private final SpeakingSessionQueryService sessionQueryService;
    private final SpeakingSessionLifecycleService lifecycleService;
    private final SpeakingTurnQueryService turnQueryService;
    private final SpeakingAssistanceAiRequestFactory aiRequestFactory;
    private final SpeakingAiClient speakingAiClient;
    private final SpeakingAiUsageCommandService usageCommandService;

    public SpeakingAssistanceResponseDto get(
            Long userId,
            Long sessionId,
            SpeakingAssistanceRequestDto request
    ) {
        if (request == null || request.type() == null) {
            throw invalid("Assistance Type이 필요합니다.");
        }

        SpeakingSession session = sessionQueryService.getOwnedEntity(
                userId,
                sessionId
        );
        lifecycleService.expireIfNeeded(session);
        lifecycleService.requireActive(session);

        List<SpeakingTurn> turns = turnQueryService.getEntities(sessionId);
        SpeakingTurn targetTurn = resolveTargetTurn(
                userId,
                sessionId,
                request.targetTurnId(),
                turns
        );
        String assistantText = resolveAssistantText(session, targetTurn);
        if (assistantText == null || assistantText.isBlank()) {
            throw invalid("도움을 제공할 AI 질문이 아직 없습니다.");
        }

        int appliesToTurnIndex = Math.min(
                session.getMaxTurns(),
                Math.max(1, session.getCompletedTurns() + 1)
        );
        String audioUrl = targetTurn == null
                ? openingAudioUrl(session)
                : assistantAudioUrl(targetTurn);

        if (request.type() == AssistanceType.REPLAY) {
            return response(
                    request.type(),
                    targetTurn,
                    appliesToTurnIndex,
                    null,
                    audioUrl,
                    1.0
            );
        }
        if (request.type() == AssistanceType.SLOW_PLAYBACK) {
            return response(
                    request.type(),
                    targetTurn,
                    appliesToTurnIndex,
                    null,
                    audioUrl,
                    0.75
            );
        }
        if (request.type() == AssistanceType.SHOW_QUESTION) {
            return response(
                    request.type(),
                    targetTurn,
                    appliesToTurnIndex,
                    assistantText,
                    null,
                    1.0
            );
        }

        AiSpeakingAssistanceResponseDto aiResponse = speakingAiClient
                .generateAssistance(
                        aiRequestFactory.create(
                                session,
                                targetTurn,
                                turns,
                                request.type(),
                                assistantText
                        )
                );
        if (aiResponse != null && !aiResponse.idempotentReplay()) {
            usageCommandService.record(
                    session,
                    targetTurn == null ? null : targetTurn.getId(),
                    aiResponse.usage(),
                    0
            );
        }
        if (aiResponse == null
                || aiResponse.type() != request.type()
                || aiResponse.content() == null
                || aiResponse.content().isBlank()) {
            throw new BusinessException(
                    "Speaking Assistance 생성에 실패했습니다.",
                    LanguageLearningErrorCode.SPEAKING_ASSISTANCE_FAILED
            );
        }

        return response(
                request.type(),
                targetTurn,
                appliesToTurnIndex,
                aiResponse.content(),
                null,
                1.0
        );
    }

    private SpeakingTurn resolveTargetTurn(
            Long userId,
            Long sessionId,
            Long targetTurnId,
            List<SpeakingTurn> turns
    ) {
        if (targetTurnId != null) {
            return turnQueryService.getOwnedEntity(
                    userId,
                    sessionId,
                    targetTurnId
            );
        }
        return turns.stream()
                .filter(turn -> turn.getAssistantText() != null)
                .filter(turn -> !turn.getAssistantText().isBlank())
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private String resolveAssistantText(
            SpeakingSession session,
            SpeakingTurn turn
    ) {
        return turn == null
                ? session.getOpeningAssistantText()
                : turn.getAssistantText();
    }

    private SpeakingAssistanceResponseDto response(
            AssistanceType type,
            SpeakingTurn targetTurn,
            int appliesToTurnIndex,
            String content,
            String audioUrl,
            double playbackRate
    ) {
        return new SpeakingAssistanceResponseDto(
                type,
                targetTurn == null ? null : targetTurn.getId(),
                appliesToTurnIndex,
                content,
                audioUrl,
                playbackRate
        );
    }

    private String openingAudioUrl(SpeakingSession session) {
        if (session.getOpeningAssistantAudioObjectKey() == null) {
            return null;
        }
        return "/api/v1/language-learning/speaking/sessions/"
                + session.getId() + "/audio/opening";
    }

    private String assistantAudioUrl(SpeakingTurn turn) {
        if (turn.getAssistantAudioObjectKey() == null) {
            return null;
        }
        return "/api/v1/language-learning/speaking/sessions/"
                + turn.getSession().getId()
                + "/turns/" + turn.getId() + "/audio";
    }

    private BusinessException invalid(String message) {
        return new BusinessException(
                message,
                LanguageLearningErrorCode.SPEAKING_ASSISTANCE_FAILED
        );
    }
}
