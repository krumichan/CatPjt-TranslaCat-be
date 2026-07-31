package jp.co.translacat.domain.chat.openchat.dto.request;

public record OpenChatProfileRequestDto(
        String nickname,
        String profileImageObjectKey
) {
}
