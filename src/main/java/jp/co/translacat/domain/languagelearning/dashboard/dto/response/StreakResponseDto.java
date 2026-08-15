package jp.co.translacat.domain.languagelearning.dashboard.dto.response;

import java.time.LocalDate;

public record StreakResponseDto(
        int current,
        int longest,
        LocalDate lastStudyDate
) {
}
