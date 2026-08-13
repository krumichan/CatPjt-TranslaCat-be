package jp.co.translacat.domain.languagelearning.ai.dto.model;

import jp.co.translacat.domain.languagelearning.common.enums.KeywordSource;
import jp.co.translacat.domain.languagelearning.common.enums.KeywordType;

public record SelectedKeywordDto(
        String key,
        String text,
        KeywordSource source,
        KeywordType type,
        String canonicalKey,
        Double selectionWeight
) {
}
