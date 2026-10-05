package jp.co.translacat.novel;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.novel.domain.*;
import jp.co.translacat.novel.infrastructure.ai.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class KeyedTranslationSchemaTest {
    private final ObjectMapper json = new ObjectMapper();
    private final SourceEpisode source = new SentenceSegmenter().segment(new EpisodeKey("syosyetu", "n2604qf", "1"),
            "題", List.of("同じ文。同じ文。違う文。"), null, null);

    @Test
    void schemaRequiresEveryCompactIdAndRestoresReversedKeysWithoutChangingCanonicalIds() throws Exception {
        var prepared = new TranslationRequestFactory().prepare(source.segments(), List.of(), Map.of(), TranslationOptions.ResponseShape.KEYED_V3);
        assertThat(prepared.schema().get("required")).isEqualTo(List.of("s0", "s1", "s2"));
        assertThat(prepared.schema().get("additionalProperties")).isEqualTo(false);
        assertThat(prepared.schemaVersion()).isEqualTo("segment-map-v3-compact");
        var result = prepared.batch().restore(json.readTree("{\"s2\":\"다른 문장\",\"s1\":\"같은 문장\",\"s0\":\"같은 문장\"}"), TranslationOptions.ResponseShape.KEYED_V3);
        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(source.segments().get(0).id(), "같은 문장",
                source.segments().get(1).id(), "같은 문장", source.segments().get(2).id(), "다른 문장"));
    }

    @Test
    void missingExtraWrongTypeBlankAndArrayCannotPassKeyedValidation() throws Exception {
        var batch = CompactTranslationBatch.of(source.segments());
        for (String raw : List.of("{\"s0\":\"a\",\"s1\":\"b\"}", "{\"s0\":\"a\",\"s1\":\"b\",\"s2\":\"c\",\"extra\":\"d\"}",
                "{\"s0\":\"a\",\"s1\":false,\"s2\":\"c\"}", "{\"s0\":\"a\",\"s1\":\"　 \",\"s2\":\"c\"}", "[]")) {
            var output = json.readTree(raw);
            for (var shape : List.of(TranslationOptions.ResponseShape.KEYED_V3, TranslationOptions.ResponseShape.KEYED_V4)) {
                assertThatThrownBy(() -> batch.restore(output, shape)).isInstanceOf(NovelProblem.class);
            }
        }
    }

    @Test
    void v2FingerprintAndSchemaRemainReproducibleWhileKeyedPolicyUsesSeparateCacheIdentity() {
        var baseline = TranslationOptions.baseline("model|legacy|none|8192|standard");
        String priorFingerprint = SentenceSegmenter.hash("novel-plan-v1|0|2|LEGACY|CURRENT|8192|model|legacy|none|8192|standard");
        assertThat(baseline.fingerprint()).isEqualTo(priorFingerprint);
        var keyed = new TranslationOptions(0, 2, TranslationOptions.Strategy.LEGACY, TranslationOptions.Context.CURRENT,
                8192, "model|legacy|none|8192|standard", TranslationOptions.ResponseShape.KEYED_V3);
        assertThat(keyed.fingerprint()).isNotEqualTo(baseline.fingerprint());
        assertThat(new TranslationRequestFactory().prepare(source.segments(), List.of(), Map.of()).schemaVersion())
                .isEqualTo("segment-items-v2-compact");
        var planner = new TranslationPlanner();
        assertThat(planner.plan(source, baseline).chunks()).isEqualTo(planner.plan(source, keyed).chunks());
    }
    @Test
    void v4RequiresNonblankValuesWithoutChangingV3InstructionsOrFingerprint() throws Exception {
        var factory = new TranslationRequestFactory();
        var v3 = factory.prepare(source.segments(), List.of("前の文。"), Map.of(), TranslationOptions.ResponseShape.KEYED_V3);
        var v4 = factory.prepare(source.segments(), List.of("前の文。"), Map.of(), TranslationOptions.ResponseShape.KEYED_V4);
        var schema3 = json.valueToTree(v3.schema());
        var schema4 = json.valueToTree(v4.schema());
        for (String id : List.of("s0", "s1", "s2")) {
            assertThat(schema3.path("properties").path(id).has("pattern")).isFalse();
            assertThat(schema4.path("properties").path(id).path("pattern").asText()).isEqualTo("\\S");
        }
        assertThat(v4.schemaVersion()).isEqualTo("segment-map-v4-nonblank");
        assertThat(v4.schemaName()).isEqualTo("novel_segments_keyed_nonblank_v4");
        assertThat(v4.instructions()).isEqualTo(v3.instructions());
        assertThat(v4.data()).isEqualTo(v3.data());
        var oldOptions = new TranslationOptions(0, 2, TranslationOptions.Strategy.LEGACY, TranslationOptions.Context.CURRENT,
                8192, "model|legacy|none|8192|standard", TranslationOptions.ResponseShape.KEYED_V3);
        var newOptions = new TranslationOptions(0, 2, TranslationOptions.Strategy.LEGACY, TranslationOptions.Context.CURRENT,
                8192, oldOptions.profileIdentity(), TranslationOptions.ResponseShape.KEYED_V4);
        assertThat(oldOptions.fingerprint()).isEqualTo(SentenceSegmenter.hash("novel-plan-v1|0|2|LEGACY|CURRENT|8192|model|legacy|none|8192|standard|segment-map-v3-compact"));
        assertThat(newOptions.fingerprint()).isNotEqualTo(oldOptions.fingerprint());
        assertThat(v4.batch().restore(json.readTree("{\"s2\":\"다른 문장\",\"s1\":\"같은 문장\",\"s0\":\"같은 문장\"}"), TranslationOptions.ResponseShape.KEYED_V4).keySet())
                .containsExactlyElementsOf(source.segments().stream().map(SourceEpisode.Segment::id).toList());
    }
}
