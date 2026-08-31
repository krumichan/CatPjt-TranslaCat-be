package jp.co.translacat.domain.languagelearning.level.dto.response;

public record LevelAnswerResultResponseDto(
        Long sessionId,
        Long itemId,
        int questionNumber,
        boolean evaluable,
        Integer score,
        String reasonCode,
        boolean completed,
        LevelQuestionResponseDto nextQuestion
) {
}
