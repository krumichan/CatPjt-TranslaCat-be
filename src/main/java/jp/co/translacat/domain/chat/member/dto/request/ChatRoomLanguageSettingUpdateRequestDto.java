package jp.co.translacat.domain.chat.member.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRoomLanguageSettingUpdateRequestDto(

        @NotBlank(message = "원문 언어 코드는 필수입니다.")
        @Size(max = 10, message = "원문 언어 코드는 10자 이하로 입력해주세요.")
        String originalLanguageCode,

        @NotBlank(message = "번역 언어 코드는 필수입니다.")
        @Size(max = 10, message = "번역 언어 코드는 10자 이하로 입력해주세요.")
        String translationLanguageCode,

        boolean showOriginal,

        boolean showTranslation
) {
}