package jp.co.translacat.domain.support.dto;

import jakarta.validation.constraints.*;
import jp.co.translacat.domain.support.enums.InquiryCategory;

public record InquiryCreateRequestDto(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 10000) String content,
        @NotNull InquiryCategory category,
        @Positive Long relatedMessageId
) {
}
