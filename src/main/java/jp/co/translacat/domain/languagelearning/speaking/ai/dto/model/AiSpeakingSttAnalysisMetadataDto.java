package jp.co.translacat.domain.languagelearning.speaking.ai.dto.model;

public record AiSpeakingSttAnalysisMetadataDto(
        String provider,
        String model,
        String modelVersion,
        String detectedLanguage,
        String requestedLanguage,
        double lowConfidenceThreshold,
        double audioDuration,
        AiSpeakingAudioQualitySignalsDto audioQualitySignals,
        String normalizationVersion,
        String sttHintVersion
) {
}
