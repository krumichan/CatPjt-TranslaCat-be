package jp.co.translacat.domain.voice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Setter
@Getter
public class SoundTranslationRequestDto {

    @Schema(description = "음성 이력 그룹 ID (세션 식별자)", example = "1")
    private String groupId;

    @Schema(description = "실시간 시스템 사운드")
    private MultipartFile sound;
}
