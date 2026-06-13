package jp.co.translacat.domain.accountbook.member.dto;

import jakarta.validation.constraints.NotBlank;

public record AccountBookMemberInviteRequestDto(
        @NotBlank
        String publicId
) {
}