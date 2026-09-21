package jp.co.translacat.domain.languagelearning.practice.policy;

import com.fasterxml.jackson.core.type.TypeReference;
import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeQuestion;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/** Optional exact-surface enrichment; never a generation/completion/score gate. */
public final class ReadingPassageExpressionPolicy {
    private ReadingPassageExpressionPolicy() {
    }

    public static Set<String> completedPassages(
            List<PracticeQuestion> questions,
            Predicate<PracticeQuestion> answered
    ) {
        Set<String> completed = new LinkedHashSet<>();
        for (String passage : List.of("p1", "p2")) {
            int first = passage.equals("p1") ? 1 : 4;
            int last = passage.equals("p1") ? 3 : 5;
            boolean allAnswered = IntStream.rangeClosed(first, last).allMatch(order -> {
                List<PracticeQuestion> matches = questions.stream()
                        .filter(question -> question.getOrderNo() == order).toList();
                return matches.size() == 1
                        && passage.equals(matches.getFirst().getPassageId())
                        && answered.test(matches.getFirst());
            });
            long passageVersions = questions.stream()
                    .filter(question -> passage.equals(question.getPassageId()))
                    .map(PracticeQuestion::getPassageText).distinct().count();
            if (allAnswered && passageVersions == 1) completed.add(passage);
        }
        return Set.copyOf(completed);
    }

    public static List<String> readCandidates(
            LanguageLearningJsonCodec codec, String json, String passage
    ) {
        if (json == null || json.isBlank() || passage == null || passage.isBlank()) return List.of();
        try {
            List<Object> candidates = codec.read(json, new TypeReference<List<Object>>() {});
            if (candidates == null) return List.of();
            Set<String> result = new LinkedHashSet<>();
            for (Object candidate : candidates) {
                if (!(candidate instanceof String text)) continue;
                String surface = text.strip();
                if (!surface.isEmpty() && passage.contains(surface)) result.add(surface);
                if (result.size() == 3) break;
            }
            return List.copyOf(result);
        } catch (RuntimeException ignored) {
            // Only this optional field fails open. Prompt/options/answer parsing stays strict.
            return List.of();
        }
    }
}
