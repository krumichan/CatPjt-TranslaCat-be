package jp.co.translacat.domain.languagelearning.speaking.session.dto.response;

public record SpeakingDailyUsageResponseDto(
        long sessionCount,
        double usedMinutes,
        int dailySessionLimit,
        int dailySpeakingHardLimitMinutes,
        int dailyGoalMinutes
) {
}
