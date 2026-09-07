package jp.co.translacat.domain.languagelearning.practice.dto.response;

import java.util.List;

public record VocabularyMasterySummaryResponseDto(
        int total,
        double averageScore,
        int newCount,
        int learningCount,
        int familiarCount,
        int strongCount,
        int masteredCount,
        List<Item> weakest
) {
    public record Item(
            String canonicalKey,
            String displayExpression,
            double score,
            String stage,
            int evaluationCount
    ) {
    }
}
