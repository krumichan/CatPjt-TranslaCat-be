package jp.co.translacat.novel;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.novel.domain.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;

class ContiguousContextTest {
    private final TranslationPlanner planner = new TranslationPlanner();
    @Test void immediateThreeThousandBeforeAndOneThousandAfterCrossParagraphsWithoutSkipping() {
        var source = units(IntStream.range(0, 10).mapToObj(i -> "字".repeat(1000)).toList());
        var chunk = chunk(source, "s5");
        assertThat(chunk.context().stream().map(SourceEpisode.Segment::id)).containsExactly("s2", "s3", "s4", "s6");
        var metadata = new ObjectMapper().valueToTree(chunk).path("contextSelection");
        assertThat(metadata.path("beforeCodePoints").asInt()).isEqualTo(3000);
        assertThat(metadata.path("afterCodePoints").asInt()).isEqualTo(1000);
        assertThat(metadata.path("beforeFirstOrder").asInt()).isEqualTo(2);
        assertThat(metadata.path("afterLastOrder").asInt()).isEqualTo(6);
    }
    @Test void nearestUnitThatDoesNotFitStopsThatDirectionInsteadOfJumpingToShorterFarUnits() {
        var source = units(List.of("遠", "大".repeat(2500), "近".repeat(1000), "対象", "次".repeat(1100), "遠"));
        var chunk = chunk(source, "s3");
        assertThat(chunk.context().stream().map(SourceEpisode.Segment::id)).containsExactly("s2");
        var metadata = new ObjectMapper().valueToTree(chunk).path("contextSelection");
        assertThat(metadata.path("beforeStopReason").asText()).isEqualTo("NEAREST_UNIT_EXCEEDS_REMAINING_BUDGET");
        assertThat(metadata.path("afterStopReason").asText()).isEqualTo("NEAREST_UNIT_EXCEEDS_REMAINING_BUDGET");
    }
    @Test void supplementaryCharactersCountOnceAndWhitespaceUnitsRemainContiguousAndCountRawCodePoints() {
        var source = units(List.of("遠", "😀".repeat(1499), "\n ", "対象", "😀".repeat(500), "\n", "次".repeat(500)));
        var chunk = chunk(source, "s3");
        assertThat(chunk.context().stream().map(SourceEpisode.Segment::id)).containsExactly("s0", "s1", "s2", "s4", "s5");
        var metadata = new ObjectMapper().valueToTree(chunk).path("contextSelection");
        assertThat(metadata.path("beforeCodePoints").asInt()).isEqualTo(1502);
        assertThat(metadata.path("afterCodePoints").asInt()).isEqualTo(501);
        assertThat(metadata.path("blankPolicy").asText()).isEqualTo("PRESERVE_AND_COUNT_RAW_CODE_POINTS");
    }
    @Test void policyKeepsTargetsAndOldWideFingerprintAndHandlesSourceEdges() {
        var source = units(List.of("一", "二", "三"));
        var old = options(3, TranslationOptions.Context.WIDE);
        var changed = options(3, TranslationOptions.Context.CONTIGUOUS_WIDE_V2);
        assertThat(old.fingerprint()).isEqualTo(SentenceSegmenter.hash("novel-plan-v1|3|2|BALANCED|WIDE|32768|context-test"));
        assertThat(changed.fingerprint()).isNotEqualTo(old.fingerprint());
        assertThat(planner.plan(source, changed).chunks().stream().flatMap(c -> c.targets().stream()).map(SourceEpisode.Segment::id))
                .containsExactly("s0", "s1", "s2");
        assertThat(chunk(source, "s0").context().stream().map(SourceEpisode.Segment::id)).containsExactly("s1", "s2");
        assertThat(chunk(source, "s2").context().stream().map(SourceEpisode.Segment::id)).containsExactly("s0", "s1");
    }
    private TranslationPlanner.Chunk chunk(SourceEpisode source, String target) {
        int n = (int)source.segments().stream().filter(s -> !SourceText.isBlank(s.plainJa())).count();
        return planner.plan(source, options(n, TranslationOptions.Context.CONTIGUOUS_WIDE_V2)).chunks().stream()
                .filter(c -> c.targets().stream().anyMatch(s -> s.id().equals(target))).findFirst().orElseThrow();
    }
    private TranslationOptions options(int n, TranslationOptions.Context context) {
        return new TranslationOptions(n, 2, TranslationOptions.Strategy.BALANCED, context, 32768, "context-test");
    }
    private SourceEpisode units(List<String> text) {
        var segments = new ArrayList<SourceEpisode.Segment>();
        for (int i=0;i<text.size();i++) segments.add(new SourceEpisode.Segment("s"+i,"p"+i,i,i,0,text.get(i).length(),text.get(i),text.get(i)));
        return new SourceEpisode("source-test", SentenceSegmenter.VERSION, "題", null, null, segments);
    }
}
