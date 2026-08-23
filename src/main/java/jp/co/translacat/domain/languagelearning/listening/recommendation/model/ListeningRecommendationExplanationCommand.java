package jp.co.translacat.domain.languagelearning.listening.recommendation.model;

import java.util.List;

public record ListeningRecommendationExplanationCommand(
        List<String> sources,
        int evidenceCount,
        double recentAverage
) {
}
