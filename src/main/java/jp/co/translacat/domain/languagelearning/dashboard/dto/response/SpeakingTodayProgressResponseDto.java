package jp.co.translacat.domain.languagelearning.dashboard.dto.response;

public record SpeakingTodayProgressResponseDto(
        int completedSessions,
        double completedMinutes,
        int goalMinutes,
        String status
) {
}
