package jp.co.translacat.domain.languagelearning.speaking.ai.dto.request;

import jp.co.translacat.domain.languagelearning.ai.dto.model.LearningProfileSummaryDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingAssistantEvaluationTurnDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.model.AiSpeakingEvaluationTurnDto;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingEvaluationScope;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingPracticeMode;
import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingResultKind;

import java.util.List;

public record AiSpeakingCoachingRequestDto(
        String requestId,
        String idempotencyKey,
        String sessionId,
        String topic,
        SpeakingPracticeMode practiceMode,
        SpeakingEvaluationScope evaluationScope,
        String goal,
        String targetLevel,
        String originLanguage,
        String learningLanguage,
        List<AiSpeakingEvaluationTurnDto> userTurns,
        List<AiSpeakingAssistantEvaluationTurnDto> assistantTurns,
        String sessionSummary,
        LearningProfileSummaryDto priorProfileSummary,
        String evaluationPolicyVersion,
        int manualRetryAttempt,
        SpeakingResultKind resultKind,
        String resultPolicyVersion,
        String sourceSnapshotHash
) {
    public AiSpeakingCoachingRequestDto forManualRetry(int attempt) {
        if (attempt < 1) throw new IllegalArgumentException("Positive manual retry count required");
        return new AiSpeakingCoachingRequestDto(
                idempotencyKey + ":manual:" + attempt, idempotencyKey, sessionId, topic,
                practiceMode, evaluationScope, goal, targetLevel, originLanguage, learningLanguage,
                userTurns, assistantTurns, sessionSummary, priorProfileSummary,
                evaluationPolicyVersion, attempt, resultKind, resultPolicyVersion, sourceSnapshotHash
        );
    }
}
