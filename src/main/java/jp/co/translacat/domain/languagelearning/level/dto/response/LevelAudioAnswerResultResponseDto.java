package jp.co.translacat.domain.languagelearning.level.dto.response;

import java.time.LocalDateTime;

public record LevelAudioAnswerResultResponseDto(
        Long sessionId,
        Long itemId,
        boolean evaluable,
        Integer score,
        String reasonCode,
        boolean completed,
        LevelQuestionResponseDto nextQuestion,
        LocalDateTime retentionUntil
) {
}
