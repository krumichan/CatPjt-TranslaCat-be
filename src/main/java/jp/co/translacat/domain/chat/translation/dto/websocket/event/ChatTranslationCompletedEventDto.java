package jp.co.translacat.domain.chat.translation.dto.websocket.event;

import jp.co.translacat.domain.chat.translation.enums.ChatMessageTranslationStatus;
import jp.co.translacat.domain.chat.translation.event.ChatMessageTranslationCompletedEvent;
import jp.co.translacat.domain.chat.websocket.enums.ChatWebSocketEventType;

import java.time.LocalDateTime;

public record ChatTranslationCompletedEventDto(
        String eventType,
        Long chatRoomId,
        Long messageId,
        Long translationId,
        String languageCode,
        String translatedContent,
        ChatMessageTranslationStatus status,
        LocalDateTime occurredAt
) {

    public static ChatTranslationCompletedEventDto from(
            ChatMessageTranslationCompletedEvent event
    ) {
        return new ChatTranslationCompletedEventDto(
                ChatWebSocketEventType.TRANSLATION_COMPLETED.getEventName(),
                event.chatRoomId(),
                event.messageId(),
                event.translationId(),
                event.languageCode(),
                event.translatedContent(),
                ChatMessageTranslationStatus.COMPLETED,
                LocalDateTime.now()
        );
    }
}