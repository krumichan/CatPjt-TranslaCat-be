package jp.co.translacat.domain.languagelearning.profile.dto.response;

public record DifficultyPerformanceResponseDto(
        Double review,
        Double normal,
        Double challenge
) {
}
