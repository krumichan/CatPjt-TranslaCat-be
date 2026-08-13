package jp.co.translacat.domain.languagelearning.keyword.dto.request;

import jp.co.translacat.domain.languagelearning.common.enums.KeywordType;

public record KeywordUpdateRequestDto(
        String text,
        KeywordType type,
        String canonicalKey,
        Boolean active
) {
}
