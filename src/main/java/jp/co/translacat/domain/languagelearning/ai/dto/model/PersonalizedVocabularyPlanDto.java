package jp.co.translacat.domain.languagelearning.ai.dto.model;

import java.util.List;

public record PersonalizedVocabularyPlanDto(
        String version,
        List<VocabularyPlanItemDto> items
) {
}
