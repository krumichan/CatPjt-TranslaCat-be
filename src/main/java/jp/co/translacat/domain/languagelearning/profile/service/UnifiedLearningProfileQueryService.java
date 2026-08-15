package jp.co.translacat.domain.languagelearning.profile.service;

import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.profile.dto.response.UnifiedProfileInsightResponseDto;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UnifiedLearningProfileQueryService {

    private final RecentLearningProfileInsightQueryService recentInsightQueryService;

    public List<UnifiedProfileInsightResponseDto> getInsights(
            Long userId,
            LearningSource source,
            int limit
    ) {
        return recentInsightQueryService.getInsights(userId, source, limit);
    }
}
