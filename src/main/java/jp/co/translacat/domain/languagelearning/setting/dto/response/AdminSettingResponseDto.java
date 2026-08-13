package jp.co.translacat.domain.languagelearning.setting.dto.response;

public record AdminSettingResponseDto(
        int defaultDailySentenceCount,
        int minDailySentenceCount,
        int maxDailySentenceCount,
        int dailyKeywordMaxCount,
        int reviewAvailableDays,
        int levelRecheckRecommendationDays,
        boolean adaptiveWritingEnabled,
        boolean aiEvaluationEnabled
) {
}
