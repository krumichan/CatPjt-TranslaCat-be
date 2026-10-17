package jp.co.translacat.infrastructure.scraping.kakuyomu.enums;

import jp.co.translacat.domain.common.enums.ConvertibleEnum;
import jp.co.translacat.domain.common.enums.RankingPeriod;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum KakuyomuRankingPeriod implements ConvertibleEnum, RankingPeriod {
    DAILY("DAILY", "일간", "daily"),
    WEEKLY("WEEKLY", "주간", "weekly"),
    MONTHLY("MONTHLY", "월간", "monthly"),
    YEARLY("YEARLY", "연간", "yearly"),
    TOTAL("TOTAL", "누적", "entire");

    private final String code;
    private final String label;
    private final String urlParam;
}
