package jp.co.translacat.domain.languagelearning.practice.dto.response;

public record PracticeMetricResponseDto(
        String skillTag,
        double score,
        int sampleCount
) {
}
