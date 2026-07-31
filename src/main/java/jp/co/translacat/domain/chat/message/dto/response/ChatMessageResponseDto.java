package jp.co.translacat.domain.chat.message.dto.response;

import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.enums.ChatMessageSenderType;
import jp.co.translacat.domain.chat.message.enums.ChatMessageStatus;
import jp.co.translacat.domain.chat.message.enums.ChatMessageType;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;

import java.time.LocalDateTime;
import java.util.List;

public record ChatMessageResponseDto(
        Long id,
        Long chatRoomId,
        Long senderUserId,
        String senderName,
        String senderEmail,
        String senderProfileImageUrl,
        ChatMessageSenderType senderType,
        ChatMessageType messageType,
        String content,
        ChatMessageStatus status,
        Long unreadMemberCount,
        List<ChatMessageTranslationResponseDto> translations,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * 기존 record 생성자 호출부 호환용 overload.
     *
     * 메시지별 미확인 인원 수를 계산하지 않는 기존 테스트·호출부는
     * null을 사용하며, 일반 메시지 조회·생성 흐름에서는 명시적으로
     * unreadMemberCount를 전달한다.
     */
    public ChatMessageResponseDto(
            Long id,
            Long chatRoomId,
            Long senderUserId,
            String senderName,
            String senderEmail,
            String senderProfileImageUrl,
            ChatMessageSenderType senderType,
            ChatMessageType messageType,
            String content,
            ChatMessageStatus status,
            List<ChatMessageTranslationResponseDto> translations,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this(
                id,
                chatRoomId,
                senderUserId,
                senderName,
                senderEmail,
                senderProfileImageUrl,
                senderType,
                messageType,
                content,
                status,
                null,
                translations,
                createdAt,
                updatedAt
        );
    }

    /**
     * 기존 호출부 및 단위 테스트 호환용 overload.
     */
    public static ChatMessageResponseDto from(
            ChatMessage message,
            List<ChatMessageTranslationResponseDto> translations
    ) {
        return from(message, null, translations, null);
    }

    public static ChatMessageResponseDto from(
            ChatMessage message,
            String senderProfileImageUrl,
            List<ChatMessageTranslationResponseDto> translations
    ) {
        return from(
                message,
                senderProfileImageUrl,
                translations,
                null
        );
    }

    public static ChatMessageResponseDto from(
            ChatMessage message,
            String senderProfileImageUrl,
            List<ChatMessageTranslationResponseDto> translations,
            Long unreadMemberCount
    ) {
        return new ChatMessageResponseDto(
                message.getId(),
                message.getChatRoom().getId(),
                resolveSenderUserId(message),
                resolveSenderName(message),
                resolveSenderEmail(message),
                resolveSenderProfileImageUrl(
                        message,
                        senderProfileImageUrl
                ),
                message.getSenderType(),
                message.getMessageType(),
                message.getContent(),
                message.getStatus(),
                message.isSystemMessage()
                        ? null
                        : unreadMemberCount,
                translations,
                message.getCreatedAt(),
                message.getUpdatedAt()
        );
    }
    private static Long resolveSenderUserId(ChatMessage message) {
        if (isOpenRoom(message) || message.getSenderUser() == null) {
            return null;
        }
        return message.getSenderUser().getId();
    }

    private static String resolveSenderName(ChatMessage message) {
        if (isOpenRoom(message) || message.getSenderUser() == null) {
            return null;
        }
        return message.getSenderUser().getUsername();
    }

    private static String resolveSenderEmail(ChatMessage message) {
        if (isOpenRoom(message) || message.getSenderUser() == null) {
            return null;
        }
        return message.getSenderUser().getEmail();
    }

    private static String resolveSenderProfileImageUrl(
            ChatMessage message,
            String senderProfileImageUrl
    ) {
        return isOpenRoom(message)
                ? null
                : senderProfileImageUrl;
    }

    private static boolean isOpenRoom(ChatMessage message) {
        return message.getChatRoom() != null
                && message.getChatRoom().getRoomType()
                == ChatRoomType.OPEN;
    }

}
