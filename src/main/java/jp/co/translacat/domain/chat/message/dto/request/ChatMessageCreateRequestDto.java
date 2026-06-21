package jp.co.translacat.domain.chat.message.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageCreateRequestDto(

        @NotBlank(message = "메시지 내용은 필수입니다.")
        @Size(max = 5000, message = "메시지는 5000자 이하로 입력해주세요.")
        String content
) {
}