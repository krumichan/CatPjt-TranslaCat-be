package jp.co.translacat.domain.voice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
public class VoiceTranslationRequestDto {

    @Schema(description = "음성 이력 그룹 ID (세션 식별자)", example = "1")
    private String groupId;

    @Schema(description = "실시간 읽어들인 일본어 텍스트", example = "こんにちは")
    private String text;
}
