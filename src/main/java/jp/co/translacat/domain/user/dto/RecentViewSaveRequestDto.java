package jp.co.translacat.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.user.enums.RecentViewType;
import lombok.Getter;

@Getter
public class RecentViewSaveRequestDto {

    @NotNull
    @Schema(description = "플랫폼 식별 ID")
    private PlatformCode platformCode;

    @NotNull
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
    @Schema(description = "소설/에피소드 구분")
    private RecentViewType type;

    @NotBlank
    @Schema(description = "소설 식별 ID")
    private String novelId;

    @Schema(description = "에피소드 식별 ID")
    private String episodeId;

    @NotBlank
    @Schema(description = "제목 일본어 원본")
    private String title;

    @NotBlank
    @Schema(description = "제목 일본어 (후리가나)")
    private String titleJa;

    @Schema(description = "제목 한국어")
    private String titleKo;
}
