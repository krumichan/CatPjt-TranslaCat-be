package jp.co.translacat.domain.languagelearning.ai.dto.model;

public record DifficultyPerformanceDto(
        Double review,
        Double normal,
        Double challenge
) {
}
