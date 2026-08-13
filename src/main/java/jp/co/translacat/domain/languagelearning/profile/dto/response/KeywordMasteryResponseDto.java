package jp.co.translacat.domain.languagelearning.profile.dto.response;

import java.time.LocalDate;

public record KeywordMasteryResponseDto(
        String canonicalKey,
        double score,
        int evaluationCount,
        int selectedCount,
        LocalDate lastSelectedDate
) {
}
