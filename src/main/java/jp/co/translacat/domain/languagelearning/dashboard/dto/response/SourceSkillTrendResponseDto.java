package jp.co.translacat.domain.languagelearning.dashboard.dto.response;

import java.util.List;
import java.util.Map;

public record SourceSkillTrendResponseDto(
        String source,
        int sampleCount,
        double confidence,
        boolean collectingData,
        Map<String, List<MetricPointResponseDto>> metrics
) {
}
