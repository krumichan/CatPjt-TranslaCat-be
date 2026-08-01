package jp.co.translacat.domain.chat.openchat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jp.co.translacat.domain.chat.openchat.support.OpenChatPolicy;

public record OpenChatBanCreateRequestDto(
        @NotNull(message = "강제 퇴장 대상 OPEN 멤버 ID는 필수입니다.")
        @Positive(message = "강제 퇴장 대상 OPEN 멤버 ID는 1 이상이어야 합니다.")
        Long targetOpenChatMemberId,

        @NotBlank(message = "강제 퇴장 사유는 필수입니다.")
        @Size(
                max = OpenChatPolicy.MAX_BAN_REASON_LENGTH,
                message = "강제 퇴장 사유는 500자 이하여야 합니다."
        )
        String reason
) {
}
