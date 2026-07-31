package jp.co.translacat.domain.chat.openchat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OpenChatOwnerProfileCreateRequestDto(
        @NotBlank(message = "OPEN 채팅 닉네임은 필수입니다.")
        @Size(
                max = 50,
                message = "OPEN 채팅 닉네임은 50자 이하여야 합니다."
        )
        String nickname,

        @Size(
                max = 500,
                message = "프로필 이미지 Object Key는 500자 이하여야 합니다."
        )
        String profileImageObjectKey
) {
}
