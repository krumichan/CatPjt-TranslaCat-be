package jp.co.translacat.domain.languagelearning.speaking.ai.dto.model;

public record AiSpeakingUsageDto(
        AiSpeakingStageUsageDto stt,
        AiSpeakingStageUsageDto conversation,
        AiSpeakingStageUsageDto tts,
        AiSpeakingStageUsageDto evaluation
) {
}
