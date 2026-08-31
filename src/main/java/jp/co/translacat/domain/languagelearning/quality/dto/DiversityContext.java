package jp.co.translacat.domain.languagelearning.quality.dto;

import java.util.List;

public record DiversityContext(
        List<DiversityHistoryItem> currentSession,
        List<DiversityHistoryItem> sameFeatureRecent,
        List<DiversityHistoryItem> crossFeatureRecent,
        List<String> exactContentHashes90d
) {
    public static DiversityContext empty() {
        return new DiversityContext(List.of(), List.of(), List.of(), List.of());
    }
}
