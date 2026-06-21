package jp.co.translacat.domain.chat.message.dto.response;

import jp.co.translacat.domain.chat.translation.entity.ChatMessageTranslation;
import jp.co.translacat.domain.chat.translation.enums.ChatMessageTranslationStatus;

import java.time.LocalDateTime;

public record ChatMessageTranslationResponseDto(
        Long id,
        String languageCode,
        String translatedContent,
        ChatMessageTranslationStatus status,
        String failureReason,
        LocalDateTime completedAt
) {

    public static ChatMessageTranslationResponseDto from(
            ChatMessageTranslation translation
    ) {
        return new ChatMessageTranslationResponseDto(
                translation.getId(),
                translation.getLanguageCode(),
                translation.getTranslatedContent(),
                translation.getStatus(),
                translation.getFailureReason(),
                translation.getCompletedAt()
        );
    }
}