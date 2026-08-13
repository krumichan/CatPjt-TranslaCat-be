package jp.co.translacat.domain.languagelearning.keyword.model;

import jp.co.translacat.domain.languagelearning.ai.dto.model.SelectedKeywordDto;
import jp.co.translacat.domain.languagelearning.common.enums.KeywordType;

public record SelectedKeywordCandidate(
        SelectedKeywordDto keyword,
        KeywordType type,
        double rawWeight
) {
}
