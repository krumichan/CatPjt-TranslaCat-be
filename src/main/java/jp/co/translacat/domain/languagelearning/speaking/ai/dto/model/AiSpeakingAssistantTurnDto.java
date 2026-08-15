package jp.co.translacat.domain.languagelearning.speaking.ai.dto.model;

public record AiSpeakingAssistantTurnDto(
        String text,
        String voice,
        AiSpeakingAssistantAudioDto audio,
        AiSpeakingErrorDto ttsError
) {
}
