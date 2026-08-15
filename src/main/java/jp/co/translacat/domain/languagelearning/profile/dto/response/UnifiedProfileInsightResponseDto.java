package jp.co.translacat.domain.languagelearning.profile.dto.response;

import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;

import java.util.List;

public record UnifiedProfileInsightResponseDto(
        String patternKey,
        String direction,
        int evidenceCount,
        double weightedEvidence,
        List<LearningSource> sources,
        boolean unified,
        String recommendedFocus
) {
}
