package jp.co.translacat.domain.languagelearning.dashboard.dto.response;

public record SpeakingFeatureSummaryResponseDto(
        int sessions,
        double totalMinutes,
        Double overallAverage,
        Double fluencyAverage,
        Double pronunciationAverage,
        Double interactionAverage,
        boolean collectingData
) {
}
