package jp.co.translacat.domain.accountbook.invitation.dto;

import jakarta.validation.constraints.NotBlank;

public record AccountBookInvitationCreateRequestDto(
        @NotBlank String publicId
) {
}