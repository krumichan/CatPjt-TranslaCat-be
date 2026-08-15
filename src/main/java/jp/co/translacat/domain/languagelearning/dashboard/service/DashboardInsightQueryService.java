package jp.co.translacat.domain.languagelearning.dashboard.service;

import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.dashboard.dto.response.DashboardInsightsResponseDto;
import jp.co.translacat.domain.languagelearning.profile.dto.response.UnifiedProfileInsightResponseDto;
import jp.co.translacat.domain.languagelearning.profile.service.UnifiedLearningProfileQueryService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardInsightQueryService {

    private final UnifiedLearningProfileQueryService unifiedProfileQueryService;

    public DashboardInsightsResponseDto get(
            Long userId,
            LearningSource source
    ) {
        List<UnifiedProfileInsightResponseDto> all =
                unifiedProfileQueryService.getInsights(userId, source, 30);
        List<UnifiedProfileInsightResponseDto> strengths = all.stream()
                .filter(item -> "STRENGTH".equalsIgnoreCase(item.direction()))
                .limit(10)
                .toList();
        List<UnifiedProfileInsightResponseDto> weaknesses = all.stream()
                .filter(item -> !"STRENGTH".equalsIgnoreCase(item.direction()))
                .limit(10)
                .toList();
        List<String> focus = all.stream()
                .map(UnifiedProfileInsightResponseDto::recommendedFocus)
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(10)
                .toList();
        return new DashboardInsightsResponseDto(strengths, weaknesses, focus);
    }
}
