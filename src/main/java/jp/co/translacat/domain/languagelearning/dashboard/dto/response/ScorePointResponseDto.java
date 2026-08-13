package jp.co.translacat.domain.languagelearning.dashboard.dto.response;

import java.time.LocalDate;

public record ScorePointResponseDto(
        LocalDate date,
        double overall,
        double meaning,
        double grammar,
        double vocabulary,
        double naturalness,
        double expression
) {
}
