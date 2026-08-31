package jp.co.translacat.domain.languagelearning.level.policy;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;
import jp.co.translacat.domain.languagelearning.quality.common.LanguageLearningContentSource;
import jp.co.translacat.domain.languagelearning.quality.dto.DiversityContext;
import jp.co.translacat.domain.languagelearning.quality.dto.DiversityHistoryItem;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LevelTestScenarioBalancePolicyTest {

    private final LevelTestScenarioBalancePolicy policy = new LevelTestScenarioBalancePolicy();

    @Test
    void sessionPrefersUnseenCategoriesBeforeRepeatingUsedTopics() {
        DiversityContext context = new DiversityContext(
                List.of(
                        history("WORK"),
                        history("DAILY_LIFE"),
                        history("TRAVEL")
                ),
                List.of(),
                List.of(),
                List.of()
        );

        List<String> preferred = policy.preferredForSession(
                context,
                10L,
                4,
                LevelTestDomain.READING
        );

        assertThat(preferred).hasSize(4);
        assertThat(preferred).doesNotContain("WORK", "DAILY_LIFE", "TRAVEL");
    }

    @Test
    void sessionStartsSecondPassOnlyAfterAllCategoriesWereUsed() {
        List<DiversityHistoryItem> history = LevelTestScenarioBalancePolicy.CATEGORIES.stream()
                .map(LevelTestScenarioBalancePolicyTest::history)
                .toList();
        DiversityContext context = new DiversityContext(
                history,
                List.of(),
                List.of(),
                List.of()
        );

        List<String> preferred = policy.preferredForSession(
                context,
                10L,
                13,
                LevelTestDomain.LISTENING
        );

        assertThat(preferred).hasSize(4);
        assertThat(LevelTestScenarioBalancePolicy.CATEGORIES).containsAll(preferred);
    }

    @Test
    void sessionUsesRecentHistoryAsTieBreakerAcrossTests() {
        DiversityContext context = new DiversityContext(
                List.of(),
                List.of(
                        history("WORK"),
                        history("WORK"),
                        history("DAILY_LIFE"),
                        history("TRAVEL")
                ),
                List.of(),
                List.of()
        );

        List<String> preferred = policy.preferredForSession(
                context,
                99L,
                1,
                LevelTestDomain.VOCABULARY
        );

        assertThat(preferred).hasSize(4);
        assertThat(preferred).doesNotContain("WORK");
    }

    @Test
    void poolGenerationTargetsOneUnderrepresentedCategory() {
        List<String> recent = List.of(
                "WORK", "WORK", "WORK",
                "DAILY_LIFE", "DAILY_LIFE",
                "TRAVEL"
        );

        List<String> preferred = policy.preferredForPool(
                recent,
                7,
                LevelTestDomain.READING,
                "reading-detail-b3"
        );

        assertThat(preferred).hasSize(1);
        assertThat(preferred).doesNotContain("WORK", "DAILY_LIFE", "TRAVEL");
    }

    @Test
    void acceptsOnlyServerPreferredCategoryWhenPlanExists() {
        assertThat(policy.accepts("HOBBY", List.of("HOBBY", "FOOD"))).isTrue();
        assertThat(policy.accepts("WORK", List.of("HOBBY", "FOOD"))).isFalse();
        assertThat(policy.accepts("WORK", List.of())).isTrue();
    }

    @Test
    void twentyQuestionSessionCoversWholePaletteBeforeOverRepresentingAnyTopic() {
        java.util.ArrayList<DiversityHistoryItem> history = new java.util.ArrayList<>();
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();

        for (int questionNumber = 1; questionNumber <= 20; questionNumber++) {
            DiversityContext context = new DiversityContext(
                    List.copyOf(history),
                    List.of(),
                    List.of(),
                    List.of()
            );
            LevelTestDomain domain = switch (questionNumber) {
                case 1, 2, 3 -> LevelTestDomain.VOCABULARY;
                case 4, 5, 6 -> LevelTestDomain.GRAMMAR;
                case 7, 8, 9, 10 -> LevelTestDomain.READING;
                case 11, 12, 13, 14 -> LevelTestDomain.LISTENING;
                case 15, 16, 17 -> LevelTestDomain.WRITING;
                default -> LevelTestDomain.SPEAKING;
            };

            List<String> preferred = policy.preferredForSession(
                    context,
                    77L,
                    questionNumber,
                    domain
            );
            String chosen = preferred.getFirst();
            history.add(history(chosen));
            counts.merge(chosen, 1, Integer::sum);

            if (questionNumber <= LevelTestScenarioBalancePolicy.CATEGORIES.size()) {
                assertThat(counts).hasSize(questionNumber);
            }
        }

        assertThat(counts).hasSize(LevelTestScenarioBalancePolicy.CATEGORIES.size());
        assertThat(counts.values()).allMatch(count -> count >= 1 && count <= 2);
        assertThat(counts.getOrDefault("WORK", 0)).isLessThanOrEqualTo(2);
    }

    private static DiversityHistoryItem history(String scenarioCategory) {
        return new DiversityHistoryItem(
                LanguageLearningContentSource.LEVEL_TEST,
                "content-" + scenarioCategory,
                null,
                scenarioCategory,
                "REPORT",
                "ARCHETYPE",
                List.of(),
                scenarioCategory,
                0
        );
    }
}
