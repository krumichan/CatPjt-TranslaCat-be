package jp.co.translacat.domain.languagelearning.ai.dto.model;

public record WritingSkillScoresDto(
        double meaning,
        double grammar,
        double vocabulary,
        double naturalness,
        double expression
) {
}
