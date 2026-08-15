package jp.co.translacat.domain.languagelearning.speaking.ai.dto.model;

public record AiSpeakingConversationMessageDto(
        String role,
        String text,
        String turnId
) {
}
