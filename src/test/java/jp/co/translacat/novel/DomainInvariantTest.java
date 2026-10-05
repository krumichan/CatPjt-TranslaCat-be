package jp.co.translacat.novel;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.novel.application.NovelAudioService;
import jp.co.translacat.novel.domain.EpisodeKey;
import jp.co.translacat.novel.domain.NovelProblem;
import jp.co.translacat.novel.domain.SentenceSegmenter;
import jp.co.translacat.novel.domain.SourceEpisode;
import jp.co.translacat.novel.domain.TranslationPolicy;
import jp.co.translacat.novel.infrastructure.ai.StrictTranslationValidator;
import jp.co.translacat.novel.infrastructure.source.SyosyetuSourceAdapter;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

class DomainInvariantTest {
    private final EpisodeKey key = new EpisodeKey("syosyetu", "n123aa", "1");
    private final SentenceSegmenter segmenter = new SentenceSegmenter();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void reconstructionPreservesQuotesWhitespaceEmptyParagraphsAndDuplicatePositions() {
        // 준비
        List<String> paragraphs = List.of("「本当！？」「本当！？」\n　彼は3.14を見た……。", "", "同じ。 同じ。", "x😀".repeat(900));

        // 실행
        SourceEpisode source = segmenter.segment(key, "題", paragraphs, null, "2");
        SourceEpisode repeated = segmenter.segment(key, "題", paragraphs, null, "2");

        // 검증
        assertThat(source).isEqualTo(repeated);
        assertThat(source.segments()).extracting(SourceEpisode.Segment::id).doesNotHaveDuplicates();
        for (int p = 0; p < paragraphs.size(); p++) {
            int index = p;
            String reassembled = source.segments().stream().filter(s -> s.paragraphIndex() == index)
                    .map(SourceEpisode.Segment::rawJa).collect(Collectors.joining());
            assertThat(reassembled).isEqualTo(paragraphs.get(p));
        }
        assertThat(source.segments()).allSatisfy(segment -> {
            assertThat(segment.rawJa()).isEqualTo(segment.plainJa());
            if (!segment.rawJa().isEmpty()) {
                assertThat(Character.isHighSurrogate(segment.rawJa().charAt(segment.rawJa().length() - 1))).isFalse();
            }
        });
    }

    @Test
    void revisionAndCacheIdentityIncludePositionScopeModelAndSource() {
        // 준비
        SourceEpisode first = segmenter.segment(key, "題", List.of("ab", "c"), null, null);
        SourceEpisode edited = segmenter.segment(key, "題", List.of("a", "bc"), null, null);
        TranslationPolicy policy = new TranslationPolicy();

        // 실행 및 검증
        assertThat(first.revision()).isNotEqualTo(edited.revision());
        assertThat(policy.cacheKey(key, first, 1, "model-a"))
                .isNotEqualTo(policy.cacheKey(key, first, 2, "model-a"))
                .isNotEqualTo(policy.cacheKey(key, first, 1, "model-b"))
                .isNotEqualTo(policy.cacheKey(key, edited, 1, "model-a"));
        assertThatThrownBy(() -> new EpisodeKey("syosyetu", "../../localhost", "1")).isInstanceOf(NovelProblem.class);
        assertThatThrownBy(() -> new EpisodeKey("kakuyomu", "n123aa", "1")).hasMessage("PLATFORM_NOT_SUPPORTED");
    }

    @Test
    void strictOutputMapsReversedIdsAndRejectsEveryMalformedSetWithoutEditingQuotes() throws Exception {
        // 준비
        List<SourceEpisode.Segment> units = segmenter.segment(key, "", List.of("一。二。"), null, null).segments();
        String first = units.getFirst().id();
        String second = units.getLast().id();
        StrictTranslationValidator validator = new StrictTranslationValidator();
        var valid = json.valueToTree(Map.of("items", List.of(Map.of("id", second, "text", "\"둘\""), Map.of("id", first, "text", "ABC"))));

        // 실행 및 검증
        assertThat(validator.validate(valid, units)).containsEntry(second, "\"둘\"").containsEntry(first, "ABC");
        List<Object> malformed = List.of(
                Map.of("items", List.of(Map.of("id", first, "text", "하나"))),
                Map.of("items", List.of(Map.of("id", first, "text", "하나"), Map.of("id", first, "text", "둘"))),
                Map.of("items", List.of(Map.of("id", first, "text", "하나"), Map.of("id", "extra", "text", "둘"))),
                Map.of("items", List.of(Map.of("id", first, "text", 2), Map.of("id", second, "text", "둘"))),
                Map.of("items", List.of(Map.of("id", first, "text", "  "), Map.of("id", second, "text", "둘"))),
                Map.of("items", valid.get("items"), "extra", true));
        for (Object bad : malformed) {
            assertThatThrownBy(() -> validator.validate(json.valueToTree(bad), units)).hasMessage("TRANSLATION_SCHEMA_INVALID");
        }
        assertThatThrownBy(() -> validator.validate(json.readTree("{\"items\":null}"), units)).isInstanceOf(NovelProblem.class);
    }

    @Test
    void parserExcludesNotesRemovesRubyReadingsAndNeverExecutesOrFollowsUntrustedHtml() {
        // 준비
        String html = "<h1 class='p-novel__title'>題</h1><div class='js-novel-text'>"
                + "<p id='Lp1'>前書き</p><p id='L1'><ruby>猫<rt>ねこ</rt><rp>(</rp></ruby>。<br>"
                + "&lt;script&gt;literal&lt;/script&gt;<script>bad()</script></p><p id='L2'></p><p id='La1'>後書き</p></div>"
                + "<a class='c-pager__item--next' href='http://127.0.0.1/secret'>次</a>";

        // 실행
        SourceEpisode parsed = new SyosyetuSourceAdapter().parse(key, html);

        // 검증
        assertThat(parsed.segments().stream().map(SourceEpisode.Segment::plainJa).collect(Collectors.joining()))
                .isEqualTo("猫。\n<script>literal</script>");
        assertThat(parsed.nextEpisodeId()).isNull();
        assertThat(parsed.segments()).anyMatch(s -> s.plainJa().isEmpty());
        assertThat(parsed.segments().getFirst().rubyTokens())
                .containsExactly(new SourceEpisode.RubyToken(0, 1, "猫", "ねこ"));
    }

    @Test
    void speechPartsPreserveEverythingWithinUtf8LimitAndTranslationChunksBatchMultipleSentences() {
        // 준비
        String text = "猫😀는 3.14를 보았다。".repeat(250);
        SourceEpisode source = segmenter.segment(key, "", List.of("猫。犬。鳥。"), null, null);

        // 실행
        List<String> parts = NovelAudioService.speechParts(text);
        var chunks = new TranslationPolicy().chunks(source.segments());

        // 검증
        assertThat(String.join("", parts)).isEqualTo(text);
        assertThat(parts).allSatisfy(part -> assertThat(part.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(1200));
        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst()).hasSize(3);
        assertThatThrownBy(() -> segmenter.segment(key, "", List.of("あ".repeat(100_001)), null, null)).hasMessage("SOURCE_SIZE_INVALID");
    }
}
