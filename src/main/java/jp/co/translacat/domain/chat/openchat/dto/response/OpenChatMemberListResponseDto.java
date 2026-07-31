package jp.co.translacat.domain.chat.openchat.dto.response;

import java.util.List;

public record OpenChatMemberListResponseDto(
        List<OpenChatMemberProfileResponseDto> members
) {

    public static OpenChatMemberListResponseDto of(
            List<OpenChatMemberProfileResponseDto> members
    ) {
        return new OpenChatMemberListResponseDto(List.copyOf(members));
    }
}
