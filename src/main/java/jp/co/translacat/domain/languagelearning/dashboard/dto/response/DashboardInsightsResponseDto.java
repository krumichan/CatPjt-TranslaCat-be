package jp.co.translacat.domain.languagelearning.dashboard.dto.response;

import jp.co.translacat.domain.languagelearning.profile.dto.response.UnifiedProfileInsightResponseDto;

import java.util.List;

public record DashboardInsightsResponseDto(
        List<UnifiedProfileInsightResponseDto> strengths,
        List<UnifiedProfileInsightResponseDto> weaknesses,
        List<String> recommendedFocus
) {
}
