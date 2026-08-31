package jp.co.translacat.domain.languagelearning.quality.dto;

public record DiversitySummary(
        String policyVersion,
        Integer candidateCount,
        Integer acceptedCount,
        Integer rejectedExact,
        Integer rejectedSimilarity,
        Integer rejectedStructural,
        Integer rejectedBackgroundKnowledge,
        Boolean fallbackUsed
) {
}
