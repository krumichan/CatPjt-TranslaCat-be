package jp.co.translacat.domain.languagelearning.ai.dto.model;

public record WritingEvaluationScoresDto(
        int overall,
        int meaning,
        int grammar,
        int vocabulary,
        int naturalness,
        int expression
) {
}
