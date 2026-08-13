package jp.co.translacat.domain.languagelearning.dashboard.dto.response;

import java.time.LocalDate;

public record RecentLearningResponseDto(
        LocalDate learningDate,
        int sentenceCount,
        String status,
        Double averageScore
) {
}
