package jp.co.translacat.domain.languagelearning.speaking.ai.dto.model;

public record AiSpeakingStageUsageDto(
        int latencyMs,
        int inputTokens,
        int outputTokens,
        double audioSeconds,
        int ttsCharacters,
        double ttsAudioSeconds,
        String provider,
        String model,
        String promptVersion,
        String evaluationVersion
) {
}
