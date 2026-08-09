package jp.co.translacat.domain.chat.ai.dto.server;

import jp.co.translacat.domain.chat.ai.enums.ChatAiTriggerType;
import jp.co.translacat.domain.chat.message.enums.ChatMessageSenderType;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;

import java.time.LocalDateTime;
import java.util.List;

public record ChatAiReplyRequestDto(
        String requestId,
        ChatAiTriggerType triggerType,
        Room room,
        AiMember aiMember,
        TriggerMessage triggerMessage,
        List<ContextMessage> contextMessages,
        int contextMaxMessages,
        int contextMaxCharacters,
        int replyMaxCharacters
) {
    public record Room(
            Long roomId,
            ChatRoomType roomType,
            String name,
            String description
    ) {
    }

    public record AiMember(
            Long aiMemberId,
            String nickname,
            String bio,
            String personaPrompt,
            String originalLanguageCode
    ) {
    }

    public record TriggerMessage(
            Long messageId,
            String senderId,
            String senderName,
            String content,
            LocalDateTime createdAt
    ) {
    }

    public record ContextMessage(
            Long messageId,
            ChatMessageSenderType senderType,
            String senderId,
            String senderName,
            String content,
            LocalDateTime createdAt
    ) {
    }
}
