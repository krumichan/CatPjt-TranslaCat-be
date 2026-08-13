package jp.co.translacat.domain.languagelearning.keyword.dto.response;

import jp.co.translacat.domain.languagelearning.common.enums.KeywordSource;
import jp.co.translacat.domain.languagelearning.common.enums.KeywordType;

import java.time.LocalDate;

public record KeywordResponseDto(
        Long id,
        String text,
        KeywordSource source,
        KeywordType type,
        String canonicalKey,
        boolean active,
        boolean selected,
        LocalDate pendingEffectiveDate
) {
}
