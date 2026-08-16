package jp.co.translacat.domain.languagelearning.keyword.dto.request;

import jp.co.translacat.domain.languagelearning.common.enums.KeywordType;

public record KeywordCreateRequestDto(
        String text,
        KeywordType type,
        String canonicalKey,
        Long parentKeywordId,
        Integer sortOrder
) {
}
