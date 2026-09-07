package jp.co.translacat.domain.languagelearning.practice.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record PracticeAttemptResponseDto(
        Long attemptId,
        int attemptNo,
        List<String> answer,
        boolean correct,
        boolean official,
        LocalDateTime submittedAt
) {
}
