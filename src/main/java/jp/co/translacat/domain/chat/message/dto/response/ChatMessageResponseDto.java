package jp.co.translacat.domain.chat.message.dto.response;

import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.enums.ChatMessageSenderType;
import jp.co.translacat.domain.chat.message.enums.ChatMessageStatus;
import jp.co.translacat.domain.chat.message.enums.ChatMessageType;

import java.time.LocalDateTime;
import java.util.List;

public record ChatMessageResponseDto(
        Long id,
        Long chatRoomId,
        Long senderUserId,
        String senderName,
        String senderEmail,
        ChatMessageSenderType senderType,
        ChatMessageType messageType,
        String content,
        ChatMessageStatus status,
        List<ChatMessageTranslationResponseDto> translations,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ChatMessageResponseDto from(
            ChatMessage message,
            List<ChatMessageTranslationResponseDto> translations
    ) {
        return new ChatMessageResponseDto(
                message.getId(),
                message.getChatRoom().getId(),
                message.getSenderUser() != null ? message.getSenderUser().getId() : null,
                message.getSenderUser() != null ? message.getSenderUser().getUsername() : null,
                message.getSenderUser() != null ? message.getSenderUser().getEmail() : null,
                message.getSenderType(),
                message.getMessageType(),
                message.getContent(),
                message.getStatus(),
                translations,
                message.getCreatedAt(),
                message.getUpdatedAt()
        );
    }
}