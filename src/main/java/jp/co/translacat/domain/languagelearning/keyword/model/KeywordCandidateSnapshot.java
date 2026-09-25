package jp.co.translacat.domain.languagelearning.keyword.model;

import jp.co.translacat.domain.languagelearning.common.enums.KeywordSource;
import jp.co.translacat.domain.languagelearning.common.enums.KeywordType;

import java.time.LocalDate;

/**
 * 카탈로그/선택 상태는 LL 소유이고 숙련도 가중치는 Core 평가 트랜잭션에 남아 있다.
 */
public record KeywordCandidateSnapshot(
        String key, String text, KeywordSource source, KeywordType type,
        String canonicalKey, LocalDate availableFrom
) {
}
