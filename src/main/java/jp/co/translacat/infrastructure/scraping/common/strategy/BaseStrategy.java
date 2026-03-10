package jp.co.translacat.infrastructure.scraping.common.strategy;

import jp.co.translacat.domain.common.enums.PlatformCode;

public interface BaseStrategy {
    PlatformCode getPlatformCode();
}
