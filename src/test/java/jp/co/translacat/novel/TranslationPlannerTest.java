package jp.co.translacat.novel;

import jp.co.translacat.novel.domain.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;

class TranslationPlannerTest {
    private final EpisodeKey key = new EpisodeKey("syosyetu", "n123aa", "1");
    private final TranslationPlanner planner = new TranslationPlanner();

    @Test
    void requestedChunksAndConcurrencyAreIndependentAndEveryTargetOccursExactlyOnce() {
        var source = source(IntStream.range(0, 180).mapToObj(i -> "番号" + i + "の猫が歩いた。犬も歩いた。").toList());
        var ids = source.segments().stream().map(SourceEpisode.Segment::id).toList();
        for (int n : new int[]{1, 2, 3, 5, 8, 10, 16}) {
            for (int c : new int[]{1, 2, 4, 5, 6, 8, 10}) {
                if (c > n) continue;
                var plan = planner.plan(source, options(n, c, "BALANCED", "SMALL", 32768));
                assertThat(plan.requestedN()).isEqualTo(n);
                assertThat(plan.actualN()).isEqualTo(n);
                assertThat(plan.effectiveC()).isEqualTo(c);
                assertThat(plan.chunks().stream().flatMap(chunk -> chunk.targets().stream()).map(SourceEpisode.Segment::id))
                        .containsExactlyElementsOf(ids);
                assertThat(plan.chunks()).allSatisfy(chunk -> assertThat(chunk.targets()).isNotEmpty());
            }
        }
    }

    @Test
    void outputSafetyAndTooFewSentencesReportActualPlanWithoutTruncatingOrEmptyChunks() {
        var source = source(List.of("猫".repeat(1000) + "。", "犬".repeat(1000) + "。", "鳥。"));
        var bounded = planner.plan(source, options(1, 10, "BALANCED", "SMALL", 4096));
        assertThat(bounded.actualN()).isGreaterThan(1);
        assertThat(bounded.adjustmentReasons()).contains("OUTPUT_TOKEN_SAFETY");
        assertThat(bounded.effectiveC()).isEqualTo(Math.min(10, bounded.actualN()));
        var few = planner.plan(source, options(10, 10, "BALANCED", "SMALL", 32768));
        assertThat(few.actualN()).isEqualTo(3);
        assertThat(few.adjustmentReasons()).contains("FEWER_TARGET_SEGMENTS");
        assertThat(bounded.chunks().stream().flatMap(c -> c.targets().stream()).map(SourceEpisode.Segment::plainJa))
                .containsExactlyElementsOf(source.segments().stream().map(SourceEpisode.Segment::plainJa).toList());
    }

    @Test
    void semanticBoundariesPreferWholeParagraphsAndContextNeverBecomesTarget() {
        var source = source(List.of("「猫がいる。」「本当だ。」", "二人は歩いた。橋を渡った。", "雨が降った。傘を開いた。"));
        var plan = planner.plan(source, options(3, 2, "SEMANTIC", "WIDE", 32768));
        assertThat(plan.chunks()).hasSize(3);
        assertThat(plan.chunks()).allSatisfy(chunk -> {
            assertThat(chunk.targets().stream().map(SourceEpisode.Segment::paragraphId).distinct()).hasSize(1);
            assertThat(chunk.context().stream().map(SourceEpisode.Segment::id))
                    .doesNotContainAnyElementsOf(chunk.targets().stream().map(SourceEpisode.Segment::id).toList());
        });
    }

    @Test
    void policyIdentityIncludesAllExplicitPlanAndExecutionChoicesButCanonicalIdsStayFixed() {
        var source = source(List.of("同じ文。同じ文。同じ文。"));
        var small = options(2, 1, "BALANCED", "SMALL", 32768);
        assertThat(small.fingerprint()).isNotEqualTo(options(2, 2, "BALANCED", "SMALL", 32768).fingerprint());
        assertThat(small.fingerprint()).isNotEqualTo(options(2, 1, "BALANCED", "WIDE", 32768).fingerprint());
        assertThat(small.fingerprint()).isNotEqualTo(options(2, 1, "SEMANTIC", "SMALL", 32768).fingerprint());
        assertThat(planner.plan(source, small).chunks().stream().flatMap(c -> c.targets().stream()).map(SourceEpisode.Segment::id))
                .containsExactlyElementsOf(source.segments().stream().map(SourceEpisode.Segment::id).toList());
    }

    private SourceEpisode source(List<String> paragraphs) {
        return new SentenceSegmenter().segment(key, "題", paragraphs, null, null);
    }
    private TranslationOptions options(int n, int c, String strategy, String context, int output) {
        return new TranslationOptions(n, c, TranslationOptions.Strategy.valueOf(strategy),
                TranslationOptions.Context.valueOf(context), output, "profile-test");
    }
}
