package jp.co.translacat.novel;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.novel.domain.EpisodeKey;
import jp.co.translacat.novel.domain.SentenceSegmenter;
import jp.co.translacat.novel.domain.SourceEpisode;
import jp.co.translacat.novel.infrastructure.ai.CompactTranslationBatch;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class CompactTranslationBatchTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void repeatedTextHasDistinctWireAndCanonicalIdsAndReversedOutputRestoresCorrectly() {
        // 준비
        List<SourceEpisode.Segment> source = segments("猫。猫。", "1");
        List<SourceEpisode.Segment> mutableInput = new ArrayList<>(source);
        CompactTranslationBatch batch = CompactTranslationBatch.of(mutableInput);
        mutableInput.clear();

        // 실행
        var output = json.valueToTree(Map.of("items", List.of(
                Map.of("id", "s1", "text", "두 번째 고양이"), Map.of("id", "s0", "text", "첫 번째 고양이"))));
        var restored = batch.restore(output);

        // 검증: 요청 순서/텍스트로 추측하지 않고 해당 호출의 불변 대응만 사용한다.
        assertThat(batch.wireSegments()).extracting(SourceEpisode.Segment::id).containsExactly("s0", "s1");
        assertThat(restored).containsExactlyInAnyOrderEntriesOf(Map.of(source.get(0).id(), "첫 번째 고양이", source.get(1).id(), "두 번째 고양이"));
        assertThat(source).extracting(SourceEpisode.Segment::id).doesNotHaveDuplicates();
        assertThatThrownBy(() -> batch.wireSegments().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> restored.clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void missingDuplicateExtraCanonicalAndWrongTypeIdsNeverPartiallyRestore() {
        // 준비
        List<SourceEpisode.Segment> source = segments("猫。犬。", "1");
        CompactTranslationBatch batch = CompactTranslationBatch.of(source);
        List<Object> invalid = List.of(
                List.of(Map.of("id", "s0", "text", "하나")),
                List.of(Map.of("id", "s0", "text", "하나"), Map.of("id", "s0", "text", "둘")),
                List.of(Map.of("id", "s0", "text", "하나"), Map.of("id", "s2", "text", "둘")),
                List.of(Map.of("id", "s0", "text", "하나"), Map.of("id", source.get(1).id(), "text", "둘")),
                List.of(Map.of("id", 0, "text", "하나"), Map.of("id", "s1", "text", "둘")));

        // 실행 및 검증
        for (Object values : invalid) {
            assertThatThrownBy(() -> batch.restore(json.valueToTree(Map.of("items", values))))
                    .hasMessage("TRANSLATION_SCHEMA_INVALID");
        }
        assertThatThrownBy(() -> CompactTranslationBatch.of(List.of(source.getFirst(), source.getFirst())))
                .hasMessage("TRANSLATION_BATCH_INVALID");
    }

    @Test
    void concurrentCallsUsingSameAliasesCannotCrossTheirCanonicalMappings() throws Exception {
        // 준비: 다른 회차의 같은 text는 다른 canonical ID 집합을 가진다.
        List<SourceEpisode.Segment> first = segments("猫。犬。", "1");
        List<SourceEpisode.Segment> second = segments("猫。犬。", "2");
        CompactTranslationBatch a = CompactTranslationBatch.of(first);
        CompactTranslationBatch b = CompactTranslationBatch.of(second);
        var output = json.valueToTree(Map.of("items", List.of(Map.of("id", "s0", "text", "고양이"), Map.of("id", "s1", "text", "개"))));
        var executor = Executors.newFixedThreadPool(4);

        try {
            // 실행 및 검증
            List<java.util.concurrent.Future<?>> requests = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                boolean even = i % 2 == 0;
                requests.add(executor.submit(() -> {
                    var restored = (even ? a : b).restore(output);
                    assertThat(restored.keySet()).containsExactlyInAnyOrderElementsOf(
                            (even ? first : second).stream().map(SourceEpisode.Segment::id).toList());
                }));
            }
            for (var request : requests) {
                request.get(3, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private List<SourceEpisode.Segment> segments(String text, String episode) {
        return new SentenceSegmenter().segment(new EpisodeKey("syosyetu", "n123aa", episode), "題", List.of(text), null, null).segments();
    }
}
