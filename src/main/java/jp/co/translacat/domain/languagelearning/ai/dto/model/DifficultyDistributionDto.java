package jp.co.translacat.domain.languagelearning.ai.dto.model;

public record DifficultyDistributionDto(
        int review,
        int normal,
        int challenge
) {
}
