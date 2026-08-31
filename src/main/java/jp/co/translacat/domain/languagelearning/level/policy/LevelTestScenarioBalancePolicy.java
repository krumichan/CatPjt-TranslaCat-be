package jp.co.translacat.domain.languagelearning.level.policy;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;
import jp.co.translacat.domain.languagelearning.quality.dto.DiversityContext;
import jp.co.translacat.domain.languagelearning.quality.dto.DiversityHistoryItem;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class LevelTestScenarioBalancePolicy {

    public static final List<String> CATEGORIES = List.of(
            "DAILY_LIFE",
            "WORK",
            "TRAVEL",
            "SHOPPING",
            "FOOD",
            "SERVICE",
            "LEARNING",
            "HOBBY",
            "DIGITAL_LIFE",
            "SOCIAL",
            "SCHEDULE",
            "HEALTH_GENERAL"
    );

    private static final int SESSION_PREFERRED_LIMIT = 4;

    public List<String> preferredForSession(
            DiversityContext context,
            Long sessionId,
            int questionNumber,
            LevelTestDomain domain
    ) {
        Map<String, Integer> currentCounts = categoryCounts(
                context == null ? List.of() : context.currentSession()
        );
        Map<String, Integer> recentCounts = categoryCounts(
                context == null ? List.of() : context.sameFeatureRecent()
        );
        return leastUsedForSession(
                currentCounts,
                recentCounts,
                SESSION_PREFERRED_LIMIT,
                sessionId + ":" + questionNumber + ":"
                        + (domain == null ? "UNKNOWN" : domain.name())
        );
    }

    public List<String> preferredForPool(
            List<String> recentScenarioCategories,
            int questionNumber,
            LevelTestDomain domain,
            String salt
    ) {
        Map<String, Integer> counts = new HashMap<>();
        if (recentScenarioCategories != null) {
            for (String category : recentScenarioCategories) {
                String normalized = normalize(category);
                if (normalized != null) {
                    counts.merge(normalized, 1, Integer::sum);
                }
            }
        }
        List<String> minimum = leastUsed(
                counts,
                CATEGORIES.size(),
                questionNumber + ":" + (domain == null ? "UNKNOWN" : domain.name()) + ":" + salt
        );
        if (minimum.isEmpty()) {
            return List.of();
        }
        int index = Math.floorMod(Objects.hash(questionNumber, domain, salt), minimum.size());
        return List.of(minimum.get(index));
    }

    public boolean accepts(String scenarioCategory, List<String> preferred) {
        if (preferred == null || preferred.isEmpty()) {
            return true;
        }
        String normalized = normalize(scenarioCategory);
        return normalized != null && preferred.contains(normalized);
    }

    private List<String> leastUsedForSession(
            Map<String, Integer> currentCounts,
            Map<String, Integer> recentCounts,
            int limit,
            String salt
    ) {
        int minimumCurrent = CATEGORIES.stream()
                .mapToInt(category -> currentCounts.getOrDefault(category, 0))
                .min()
                .orElse(0);
        int rotation = Math.floorMod(Objects.hashCode(salt), CATEGORIES.size());
        return CATEGORIES.stream()
                .filter(category -> currentCounts.getOrDefault(category, 0) == minimumCurrent)
                .sorted(Comparator
                        .comparingInt((String category) -> recentCounts.getOrDefault(category, 0))
                        .thenComparingInt(category -> rotatedRank(category, rotation)))
                .limit(Math.max(1, limit))
                .toList();
    }

    private List<String> leastUsed(
            Map<String, Integer> counts,
            int limit,
            String salt
    ) {
        int minimum = CATEGORIES.stream()
                .mapToInt(category -> counts.getOrDefault(category, 0))
                .min()
                .orElse(0);
        List<String> minimumCategories = CATEGORIES.stream()
                .filter(category -> counts.getOrDefault(category, 0) == minimum)
                .toList();

        // While unseen categories remain, never offer an already-used category. This
        // makes a 20-question session cover the whole neutral scenario palette before
        // beginning the second pass. The rotating tie-break avoids coupling one item
        // type to one permanent topic.
        Set<String> candidates = new LinkedHashSet<>(minimumCategories);
        int rotation = Math.floorMod(Objects.hashCode(salt), CATEGORIES.size());
        List<String> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparingInt(category -> rotatedRank(category, rotation)));
        return ordered.stream()
                .limit(Math.max(1, Math.min(limit, ordered.size())))
                .toList();
    }

    private Map<String, Integer> categoryCounts(List<DiversityHistoryItem> history) {
        Map<String, Integer> counts = new HashMap<>();
        if (history == null) {
            return counts;
        }
        for (DiversityHistoryItem item : history) {
            String normalized = normalize(item == null ? null : item.scenarioCategory());
            if (normalized != null) {
                counts.merge(normalized, 1, Integer::sum);
            }
        }
        return counts;
    }

    private int rotatedRank(String category, int rotation) {
        int index = CATEGORIES.indexOf(category);
        return Math.floorMod(index - rotation, CATEGORIES.size());
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return CATEGORIES.contains(normalized) ? normalized : null;
    }
}
