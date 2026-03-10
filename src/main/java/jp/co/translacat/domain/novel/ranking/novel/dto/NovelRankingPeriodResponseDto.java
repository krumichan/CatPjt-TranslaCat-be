package jp.co.translacat.domain.novel.ranking.novel.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NovelRankingPeriodResponseDto {
    @Schema(description = "period code", example = "weekly")
    private String code;
    
    @Schema(description = "period label", example = "주간")
    private String label;
}
