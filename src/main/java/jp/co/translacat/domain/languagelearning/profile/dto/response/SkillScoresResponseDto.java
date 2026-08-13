package jp.co.translacat.domain.languagelearning.profile.dto.response;

public record SkillScoresResponseDto(
        Double meaning,
        Double grammar,
        Double vocabulary,
        Double naturalness,
        Double expression
) {
}
