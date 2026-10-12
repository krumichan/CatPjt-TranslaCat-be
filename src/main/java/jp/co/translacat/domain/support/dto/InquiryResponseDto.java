package jp.co.translacat.domain.support.dto;

import jp.co.translacat.domain.support.entity.SupportInquiry;
import jp.co.translacat.domain.support.enums.InquiryCategory;
import jp.co.translacat.domain.support.enums.InquiryStatus;

import java.time.LocalDateTime;
import java.util.List;

public record InquiryResponseDto(
        Long id,
        String title,
        String content,
        InquiryCategory category,
        InquiryStatus status,
        SupportUserResponseDto user,
        int replyCount,
        Long relatedMessageId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ReplyResponseDto> replies,
        boolean unread
) {

    public static InquiryResponseDto from(
            SupportInquiry inquiry, List<ReplyResponseDto> replies, boolean unread
    ) {
        return new InquiryResponseDto(
                inquiry.getId(), inquiry.getTitle(), inquiry.getContent(), inquiry.getCategory(),
                inquiry.getStatus(), SupportUserResponseDto.from(inquiry.getUser()),
                inquiry.getReplyCount(), inquiry.getRelatedMessageId(),
                inquiry.getCreatedAt(), inquiry.getUpdatedAt(), replies, unread
        );
    }
}
