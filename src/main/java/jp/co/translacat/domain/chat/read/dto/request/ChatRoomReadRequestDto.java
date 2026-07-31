package jp.co.translacat.domain.chat.read.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ChatRoomReadRequestDto(
        @NotNull
        @Positive
        Long lastReadMessageId
) {
}
