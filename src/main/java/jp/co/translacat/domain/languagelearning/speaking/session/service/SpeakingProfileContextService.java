package jp.co.translacat.domain.languagelearning.speaking.session.service;

import jp.co.translacat.domain.languagelearning.ai.dto.model.LearningProfileSummaryDto;
import jp.co.translacat.domain.languagelearning.profile.service.LearningProfileAiContextService;
import jp.co.translacat.domain.languagelearning.profile.service.RecentLearningProfileInsightQueryService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SpeakingProfileContextService {

    private static final int MAX_RECOMMENDED_FOCUS = 10;

    private final LearningProfileAiContextService phase1ContextService;
    private final RecentLearningProfileInsightQueryService recentInsightQueryService;

    public LearningProfileSummaryDto build(Long userId) {
        LearningProfileSummaryDto base = phase1ContextService.buildSummary(userId);
        List<String> speakingFocus = recentInsightQueryService
                .getSpeakingRecommendedFocus(userId, MAX_RECOMMENDED_FOCUS);
        if (base == null) {
            return null;
        }

        LinkedHashSet<String> focus = new LinkedHashSet<>();
        if (speakingFocus != null) {
            focus.addAll(speakingFocus);
        }
        if (base.recommendedFocus() != null) {
            focus.addAll(base.recommendedFocus());
        }

        return new LearningProfileSummaryDto(
                base.profileVersion(),
                base.baseLevelScore(),
                base.skillScores(),
                base.grammarWeaknesses(),
                base.keywordMasteries(),
                base.difficultyPerformance(),
                base.errorPatterns(),
                base.trend(),
                base.confidence(),
                base.strengths(),
                base.weaknesses(),
                focus.stream().limit(MAX_RECOMMENDED_FOCUS).toList(),
                base.additionalSignals()
        );
    }
}
