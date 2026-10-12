package jp.co.translacat.domain.support.dto;

import jp.co.translacat.domain.support.entity.SupportNotice;
import jp.co.translacat.domain.support.enums.NoticeType;

import java.time.LocalDateTime;

public record NoticeResponseDto(
        Long id,
        String title,
        String content,
        NoticeType noticeType,
        String authorName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static NoticeResponseDto from(SupportNotice notice) {
        return new NoticeResponseDto(
                notice.getId(), notice.getTitle(), notice.getContent(), notice.getNoticeType(),
                SupportUserResponseDto.displayName(notice.getAuthor()), notice.getCreatedAt(), notice.getUpdatedAt()
        );
    }
}
