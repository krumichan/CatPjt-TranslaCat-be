package jp.co.translacat.domain.languagelearning.ai.dto.model;

public record LevelTestAiUsageDto(
        Integer latencyMs,
        Integer inputTokens,
        Integer outputTokens,
        String provider,
        String model,
        String promptVersion,
        String evaluationVersion
) {
}
