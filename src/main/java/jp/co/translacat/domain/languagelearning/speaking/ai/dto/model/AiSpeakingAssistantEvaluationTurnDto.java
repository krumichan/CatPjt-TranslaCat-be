package jp.co.translacat.domain.languagelearning.speaking.ai.dto.model;

public record AiSpeakingAssistantEvaluationTurnDto(
        String turnId,
        int turnIndex,
        String text
) {
}
