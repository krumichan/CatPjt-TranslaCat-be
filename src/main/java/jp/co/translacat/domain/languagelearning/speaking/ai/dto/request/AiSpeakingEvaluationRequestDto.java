package jp.co.translacat.domain.languagelearning.speaking.ai.dto.request;

import jp.co.translacat.domain.languagelearning.ai.dto.model.LearningProfileSummaryDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingAssistantEvaluationTurnDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingEvaluationTurnDto;

import java.util.List;

public record AiSpeakingEvaluationRequestDto(
        String requestId,
        String idempotencyKey,
        String sessionId,
        String topic,
        String goal,
        String targetLevel,
        String originLanguage,
        String learningLanguage,
        List<AiSpeakingEvaluationTurnDto> userTurns,
        List<AiSpeakingAssistantEvaluationTurnDto> assistantTurns,
        String sessionSummary,
        LearningProfileSummaryDto priorProfileSummary,
        String evaluationPolicyVersion,
        int manualRetryAttempt
) {
}
