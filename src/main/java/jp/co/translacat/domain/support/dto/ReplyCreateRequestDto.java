package jp.co.translacat.domain.support.dto;

import jakarta.validation.constraints.*;

public record ReplyCreateRequestDto(
        @NotBlank @Size(max = 10000) String content,
        @Positive Long parentId
) {
}
