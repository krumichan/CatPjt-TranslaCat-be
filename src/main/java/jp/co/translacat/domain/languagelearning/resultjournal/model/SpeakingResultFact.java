package jp.co.translacat.domain.languagelearning.resultjournal.model;

import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingEvaluationResponseDto;

/**
 * 실제 적용한 formal 판단·활동 가중치와 원본 응답을 함께 보존한다.
 */
public record SpeakingResultFact(
        String resultKind, long evaluationId, long sessionId, long activityId,
        String learningDate, String originLanguage, String learningLanguage,
        boolean formal, double activityWeight, AiSpeakingEvaluationResponseDto response
) {
    @Override
    public String toString() {
        return "SpeakingResultFact(payload=<redacted>)";
    }
}
