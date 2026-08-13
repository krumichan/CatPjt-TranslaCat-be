package jp.co.translacat.domain.languagelearning.ai.dto.model;

import java.util.List;

public record ProfileSignalsDto(
        List<String> strengthTags,
        List<String> weaknessTags,
        List<String> grammarPatterns,
        List<String> vocabularyPatterns,
        List<String> naturalnessPatterns,
        List<String> expressionPatterns,
        List<String> meaningPatterns,
        List<String> recommendedFocus
) {
}
