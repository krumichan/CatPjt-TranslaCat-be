package jp.co.translacat.domain.common.enums;

import jp.co.translacat.domain.novel.ranking.novel.dto.NovelRankingPeriodResponseDto;

public interface RankingPeriod {
    String getCode();
    String getLabel();
    String getUrlParam();

    default NovelRankingPeriodResponseDto toResponseDto() {
        return NovelRankingPeriodResponseDto.builder()
                .code(this.getCode())
                .label(this.getLabel())
                .build();
    }
}
