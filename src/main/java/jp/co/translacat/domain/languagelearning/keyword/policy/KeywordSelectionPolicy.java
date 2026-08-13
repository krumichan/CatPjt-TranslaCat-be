package jp.co.translacat.domain.languagelearning.keyword.policy;

import jp.co.translacat.domain.languagelearning.common.enums.KeywordType;
import jp.co.translacat.domain.languagelearning.keyword.model.SelectedKeywordCandidate;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
public class KeywordSelectionPolicy {

    public List<SelectedKeywordCandidate> select(
            List<SelectedKeywordCandidate> candidates,
            int limit,
            Random random
    ) {
        if (limit <= 0 || candidates.isEmpty()) {
            return List.of();
        }

        int selectionLimit = Math.min(limit, candidates.size());
        Map<KeywordType, List<SelectedKeywordCandidate>> candidatesByType =
                groupByType(candidates);
        List<SelectedKeywordCandidate> selected = new ArrayList<>();

        selectOnePerTypeWhenPossible(
                selectionLimit,
                candidatesByType,
                selected,
                random
        );
        fillRemainingCandidates(
                candidates,
                selectionLimit,
                selected,
                random
        );

        return List.copyOf(selected);
    }

    private Map<KeywordType, List<SelectedKeywordCandidate>> groupByType(
            List<SelectedKeywordCandidate> candidates
    ) {
        Map<KeywordType, List<SelectedKeywordCandidate>> result =
                new EnumMap<>(KeywordType.class);

        for (SelectedKeywordCandidate candidate : candidates) {
            result.computeIfAbsent(
                    candidate.type(),
                    ignored -> new ArrayList<>()
            ).add(candidate);
        }

        return result;
    }

    private void selectOnePerTypeWhenPossible(
            int limit,
            Map<KeywordType, List<SelectedKeywordCandidate>> candidatesByType,
            List<SelectedKeywordCandidate> selected,
            Random random
    ) {
        if (limit < 2) {
            return;
        }
        if (!candidatesByType.containsKey(KeywordType.TOPIC)) {
            return;
        }
        if (!candidatesByType.containsKey(KeywordType.VOCABULARY)) {
            return;
        }

        selected.add(weightedPick(
                candidatesByType.get(KeywordType.TOPIC),
                random
        ));
        selected.add(weightedPick(
                candidatesByType.get(KeywordType.VOCABULARY),
                random
        ));
    }

    private void fillRemainingCandidates(
            List<SelectedKeywordCandidate> candidates,
            int limit,
            List<SelectedKeywordCandidate> selected,
            Random random
    ) {
        while (selected.size() < limit) {
            List<SelectedKeywordCandidate> remaining = candidates.stream()
                    .filter(candidate -> !selected.contains(candidate))
                    .toList();

            if (remaining.isEmpty()) {
                return;
            }

            selected.add(weightedPick(remaining, random));
        }
    }

    private SelectedKeywordCandidate weightedPick(
            List<SelectedKeywordCandidate> candidates,
            Random random
    ) {
        double totalWeight = candidates.stream()
                .mapToDouble(SelectedKeywordCandidate::rawWeight)
                .sum();
        double cursor = random.nextDouble() * totalWeight;

        for (SelectedKeywordCandidate candidate : candidates) {
            cursor -= candidate.rawWeight();
            if (cursor <= 0) {
                return candidate;
            }
        }

        return candidates.get(candidates.size() - 1);
    }
}
