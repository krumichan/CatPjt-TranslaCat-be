package jp.co.translacat.domain.languagelearning.keyword.dto.response;

import jp.co.translacat.domain.languagelearning.common.enums.KeywordSource;
import jp.co.translacat.domain.languagelearning.common.enums.KeywordType;

import java.time.LocalDate;

public record KeywordResponseDto(
        Long id,
        String text,
        String displayName,
        String secondaryDisplayName,
        KeywordSource source,
        KeywordType type,
        String canonicalKey,
        Long parentKeywordId,
        String parentCanonicalKey,
        int sortOrder,
        boolean active,
        boolean selected,
        LocalDate pendingEffectiveDate
) {
}
