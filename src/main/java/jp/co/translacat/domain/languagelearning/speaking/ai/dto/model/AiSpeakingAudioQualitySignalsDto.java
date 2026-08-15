package jp.co.translacat.domain.languagelearning.speaking.ai.dto.model;

public record AiSpeakingAudioQualitySignalsDto(
        double rms,
        double peak,
        double silenceRatio,
        int sampleRate,
        int channels
) {
}
