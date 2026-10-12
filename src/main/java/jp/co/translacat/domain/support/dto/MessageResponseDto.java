package jp.co.translacat.domain.support.dto;

import jp.co.translacat.domain.support.entity.SupportMessage;

import java.time.LocalDateTime;

public record MessageResponseDto(
        Long id,
        String title,
        String content,
        String senderName,
        SupportUserResponseDto recipient,
        LocalDateTime createdAt,
        LocalDateTime readAt
) {

    public static MessageResponseDto from(SupportMessage message) {
        return new MessageResponseDto(
                message.getId(), message.getTitle(), message.getContent(),
                SupportUserResponseDto.displayName(message.getSender()), SupportUserResponseDto.from(message.getRecipient()),
                message.getCreatedAt(), message.getReadAt()
        );
    }
}
