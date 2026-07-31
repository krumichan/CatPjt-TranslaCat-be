package jp.co.translacat.domain.chat.openchat.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatVisibility;
import jp.co.translacat.domain.chat.openchat.support.OpenChatPolicy;

public record OpenChatRoomCreateRequestDto(
        @NotBlank(message = "OPEN 채팅방 이름은 필수입니다.")
        @Size(
                max = 100,
                message = "OPEN 채팅방 이름은 100자 이하여야 합니다."
        )
        String name,

        @NotBlank(message = "OPEN 채팅방 설명은 필수입니다.")
        @Size(
                max = 500,
                message = "OPEN 채팅방 설명은 500자 이하여야 합니다."
        )
        String description,

        @NotNull(message = "OPEN 채팅방 공개 범위는 필수입니다.")
        OpenChatVisibility visibility,

        @Min(
                value = OpenChatPolicy.MIN_MAX_MEMBER_COUNT,
                message = "OPEN 채팅방 최대 인원은 2명 이상이어야 합니다."
        )
        @Max(
                value = OpenChatPolicy.MAX_MEMBER_COUNT_LIMIT,
                message = "OPEN 채팅방 최대 인원은 100명 이하여야 합니다."
        )
        Integer maxMemberCount,

        @Valid
        @NotNull(message = "OWNER OPEN 프로필은 필수입니다.")
        OpenChatOwnerProfileCreateRequestDto ownerProfile
) {
}
