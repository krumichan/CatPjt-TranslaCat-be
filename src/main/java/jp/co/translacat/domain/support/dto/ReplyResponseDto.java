package jp.co.translacat.domain.support.dto;

import jp.co.translacat.domain.support.entity.SupportReply;
import jp.co.translacat.domain.user.enums.Role;

import java.time.LocalDateTime;

public record ReplyResponseDto(
        Long id,
        Long parentId,
        String content,
        Role authorRole,
        String authorName,
        LocalDateTime createdAt
) {

    public static ReplyResponseDto from(SupportReply reply) {
        return new ReplyResponseDto(
                reply.getId(), reply.getParentId(), reply.getContent(), reply.getAuthorRole(),
                SupportUserResponseDto.displayName(reply.getAuthor()), reply.getCreatedAt()
        );
    }
}
