package jp.co.translacat.infrastructure.scraping.kakuyomu.enums;

import jp.co.translacat.domain.common.enums.RankingPeriod;
import jp.co.translacat.domain.common.enums.ConvertibleEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum KakuyomuRankingPeriod implements ConvertibleEnum, RankingPeriod {
    ;

    private final String code;
    private final String label;
    private final String urlParam;
}
