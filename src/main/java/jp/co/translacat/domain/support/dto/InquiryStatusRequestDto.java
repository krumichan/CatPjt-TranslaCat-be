package jp.co.translacat.domain.support.dto;

import jakarta.validation.constraints.NotNull;
import jp.co.translacat.domain.support.enums.InquiryStatus;

public record InquiryStatusRequestDto(@NotNull InquiryStatus status) {
}
