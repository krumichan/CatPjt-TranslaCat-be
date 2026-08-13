package jp.co.translacat.domain.languagelearning.setting.dto.request;

public record AdminSettingUpdateRequestDto(
        Integer defaultDailySentenceCount,
        Integer minDailySentenceCount,
        Integer maxDailySentenceCount,
        Integer dailyKeywordMaxCount,
        Integer reviewAvailableDays,
        Integer levelRecheckRecommendationDays,
        Boolean adaptiveWritingEnabled,
        Boolean aiEvaluationEnabled
) {
}
