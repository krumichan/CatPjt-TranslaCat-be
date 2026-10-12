package jp.co.translacat.domain.support.dto;

import jakarta.validation.constraints.*;

public record MessageCreateRequestDto(
        @NotNull @Positive Long recipientId,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 10000) String content
) {
}
