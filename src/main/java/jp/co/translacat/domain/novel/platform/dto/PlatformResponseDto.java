package jp.co.translacat.domain.novel.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.novel.platform.entity.Platform;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PlatformResponseDto {
    @Schema(description = "platform ID", example = "1")
    private Long id;

    @Schema(description = "platform Code", example = "SYOSETU")
    private PlatformCode code;

    @Schema(description = "platform japanese name", example = "<ruby>小生塚<rt>しょうせつか</rt></ruby>になろう")
    private String nameJa;

    @Schema(description = "platform korean name", example = "소설가가 되자")
    private String nameKo;

    public static PlatformResponseDto of(Platform platform) {
        return PlatformResponseDto.builder()
            .id(platform.getId())
            .code(platform.getCode())
            .nameJa(platform.getNameJa())
            .nameKo(platform.getNameKo())
            .build();
    }
}
