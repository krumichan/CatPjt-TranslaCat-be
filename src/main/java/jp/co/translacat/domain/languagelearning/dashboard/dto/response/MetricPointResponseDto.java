package jp.co.translacat.domain.languagelearning.dashboard.dto.response;

import java.time.LocalDate;

public record MetricPointResponseDto(
        LocalDate date,
        double score
) {
}
