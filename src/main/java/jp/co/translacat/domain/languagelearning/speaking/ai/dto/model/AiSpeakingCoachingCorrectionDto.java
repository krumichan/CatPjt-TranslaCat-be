package jp.co.translacat.domain.languagelearning.speaking.ai.dto.model;

public record AiSpeakingCoachingCorrectionDto(
        String original,
        String improved,
        String explanation,
        String improvementLink
) {
}
