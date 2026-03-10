package jp.co.translacat.infrastructure.scraping.syosetu.enums;

import jp.co.translacat.domain.common.enums.RankingPeriod;
import jp.co.translacat.domain.common.enums.ConvertibleEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SyosetuRankingPeriod implements ConvertibleEnum, RankingPeriod {
    DAILY("DAILY", "일간", "daily"),
    WEEKLY("WEEKLY", "주간", "weekly"),
    MONTHLY("MONTHLY", "월간", "monthly"),
    QUARTER("QUARTER", "분기", "quarter"),
    YEARLY("YEARLY", "연간", "yearly"),
    TOTAL("TOTAL", "누적", "total");

    private final String code;
    private final String label;
    private final String urlParam;
}
