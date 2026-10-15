package jp.co.translacat.novel;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.infrastructure.japanese.FuriganaProcessor;
import jp.co.translacat.infrastructure.japanese.KuromojiFuriganaProcessor;
import jp.co.translacat.infrastructure.japanese.SudachiFuriganaProcessor;
import jp.co.translacat.domain.novel.episode.dto.EpisodeResponseDto;
import jp.co.translacat.domain.novel.episode.model.EpisodeContentContext;
import jp.co.translacat.domain.novel.episode.model.EpisodePagerContext;
import jp.co.translacat.domain.novel.novel.dto.NovelResponseDto;
import jp.co.translacat.domain.novel.novel.model.RawEpisodeContext;
import jp.co.translacat.domain.novel.translation.model.TranslationUnit;
import jp.co.translacat.novel.application.ReaderSnapshot;
import jp.co.translacat.novel.domain.EpisodeKey;
import jp.co.translacat.novel.domain.SentenceSegmenter;
import jp.co.translacat.novel.domain.SourceEpisode;
import jp.co.translacat.novel.infrastructure.japanese.NovelRubyFormatter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NovelRubyFormatterTest {
    private static NovelRubyFormatter formatter;

    @BeforeAll
    static void prepareDictionary() throws IOException {
        // 준비: 배포와 같은 Sudachi 사전으로 응답 전용 표기를 만든다.
        var sudachi = new SudachiFuriganaProcessor();
        ReflectionTestUtils.setField(sudachi, "dictPath", "src/main/resources/system_full.dic");
        sudachi.init();
        formatter = new NovelRubyFormatter(new FuriganaProcessor(new KuromojiFuriganaProcessor(), sudachi));
    }

    @Test
    void sudachiFillsMissingReadingsAndPreservesAuthoredRuby() {
        // 준비: 소설 원문에 확정 발음 한 개와 발음 없는 한자가 공존한다.
        String source = "遥と猫が歩く。";
        var authored = List.of(new SourceEpisode.RubyToken(0, 1, "遥", "はるか"));

        // 실행: FE 계약에 맞는 UTF-16 offset 토큰과 ruby 표시값을 각각 생성한다.
        var tokens = formatter.tokens(source, authored);
        String markup = formatter.markup(source);

        // 검증: 원문 발음은 덮어쓰지 않고, 나머지는 Sudachi로 채운다.
        assertThat(tokens.getFirst()).isEqualTo(authored.getFirst());
        assertThat(tokens).anySatisfy(token -> {
            assertThat(token.text()).isEqualTo("猫");
            assertThat(token.reading()).isEqualTo("ねこ");
        });
        assertThat(markup).contains("<ruby>猫<rt>ねこ</rt></ruby>");
    }

    @Test
    void sourceMarkupLikeCharactersStayLiteralAndOffsetsMatchOriginalText() {
        // 준비: 외부 소설 제목/본문의 태그처럼 보이는 문자열도 원문 텍스트다.
        String source = "<ruby>猫</ruby> & 猫😀";

        // 실행
        var tokens = formatter.tokens(source, List.of());
        String markup = formatter.markup(source);

        // 검증: 텍스트를 태그로 신뢰하지 않고 발음 위치는 원문과 일치한다.
        assertThat(tokens).hasSize(2);
        assertThat(tokens).allSatisfy(token ->
                assertThat(source.substring(token.startOffset(), token.endOffset())).isEqualTo(token.text()));
        assertThat(markup).contains("&lt;ruby&gt;").contains("&lt;/ruby&gt; &amp; ");
    }

    @Test
    void readerResponseAddsRubyWithoutChangingRevisionOrTranslationState() {
        // 준비: 저장된 원문에는 출처 발음이 없고 아직 번역도 없다.
        var key = new EpisodeKey("syosetu", "n123aa", "1");
        var source = new SentenceSegmenter().segment(key, "猫の旅", List.of("猫が歩く。"), null, null);
        var stored = ReaderSnapshot.of(key, source, null);

        // 실행: 요청 응답에서만 Sudachi 읽기 토큰을 생성한다.
        var displayed = stored.withRuby(formatter);

        // 검증: FE offset 계약과 원문 revision, 유료 작업 상태는 유지된다.
        assertThat(displayed.revision()).isEqualTo(stored.revision());
        assertThat(displayed.state()).isEqualTo("SOURCE_READY");
        assertThat(displayed.title().rawJa()).isEqualTo("猫の旅");
        assertThat(displayed.title().ja()).contains("<ruby>猫<rt>ねこ</rt></ruby>");
        assertThat(displayed.segments().getFirst().plainJa()).isEqualTo("猫が歩く。");
        assertThat(displayed.segments().getFirst().rubyTokens()).anySatisfy(token -> {
            assertThat(token.text()).isEqualTo("猫");
            assertThat(token.reading()).isEqualTo("ねこ");
        });
        var response = new ObjectMapper().valueToTree(displayed);
        assertThat(response.at("/title/ja").asText()).contains("<ruby>猫<rt>ねこ</rt></ruby>");
        assertThat(response.at("/segments/0/rubyTokens/0/text").asText()).isEqualTo("猫");
        assertThat(response.at("/segments/0/rubyTokens/0/reading").asText()).isEqualTo("ねこ");
        assertThat(response.at("/segments/0/plainJa").asText()).isEqualTo("猫が歩く。");
    }

    @Test
    void legacyEpisodeListAndContentsRecoverRubyFromPreviouslyPlainJa() {
        // 준비: 이미 저장된 ja 표시값은 평문이고 한국어 번역은 정상이다.
        var title = TranslationUnit.of("猫の旅", "猫の旅", "고양이의 여행");
        var entry = RawEpisodeContext.builder().sequence(1).identifier("1").title(title).build();
        var paragraph = EpisodeContentContext.builder()
                .sequence(1).content(TranslationUnit.of("猫が歩く。", "猫が歩く。", "고양이가 걷는다.")).build();

        // 실행: 목록과 구형 본문 응답에만 Sudachi 표시값을 복원한다.
        var listed = NovelResponseDto.of(entry, formatter);
        var episode = EpisodeResponseDto.of(EpisodePagerContext.empty(), title, List.of(paragraph), formatter);

        // 검증: 이전 객체와 한국어, 원문은 그대로 두고 ja만 ruby가 된다.
        assertThat(title.getJa()).isEqualTo("猫の旅");
        assertThat(listed.getTitle().getRawJa()).isEqualTo("猫の旅");
        assertThat(listed.getTitle().getKo()).isEqualTo("고양이의 여행");
        assertThat(listed.getTitle().getJa()).contains("<ruby>猫<rt>ねこ</rt></ruby>");
        assertThat(episode.getContents().getFirst().getRawJa()).isEqualTo("猫が歩く。");
        assertThat(episode.getContents().getFirst().getKo()).isEqualTo("고양이가 걷는다.");
        assertThat(episode.getContents().getFirst().getJa()).contains("<ruby>猫<rt>ねこ</rt></ruby>");
    }

    @Test
    void longerDictionaryEntryTakesPrecedenceOverOverlappingShorterEntry() throws IOException {
        // 준비: 짧은 사용자 단어와 긴 사용자 단어가 같은 원문을 공유한다.
        var sudachi = new SudachiFuriganaProcessor();
        ReflectionTestUtils.setField(sudachi, "dictPath", "src/main/resources/system_full.dic");
        sudachi.init();
        var processor = new FuriganaProcessor(new KuromojiFuriganaProcessor(), sudachi);
        var dictionary = new LinkedHashMap<String, String>();
        dictionary.put("人生", "じんせい");
        dictionary.put("人", "ひと");
        processor.syncCachedDict(dictionary);

        // 실행: 신규 reader와 목록에서 공용으로 사용하는 ruby 생성 경로를 호출한다.
        var result = new NovelRubyFormatter(processor).tokens("人生");

        // 검증: 짧은 일치가 긴 사전 항목을 가로채지 않는다.
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().text()).isEqualTo("人生");
        assertThat(result.getFirst().reading()).isEqualTo("じんせい");
    }

    @Test
    void registeredReadingInvalidatesPreviouslyGeneratedReaderRuby() throws IOException {
        // 준비: 첫 GET이 Sudachi 기본 발음을 캐시에 넣은 상태다.
        var sudachi = new SudachiFuriganaProcessor();
        ReflectionTestUtils.setField(sudachi, "dictPath", "src/main/resources/system_full.dic");
        sudachi.init();
        var processor = new FuriganaProcessor(new KuromojiFuriganaProcessor(), sudachi);
        var ruby = new NovelRubyFormatter(processor);
        var original = ruby.tokens("猫");

        // 실행: 사전 등록 후 같은 원문을 새 GET에서 다시 표시한다.
        processor.syncCachedDict(Map.of("猫", "まお"));
        var refreshed = ruby.tokens("猫");

        // 검증: 기존 응답 캐시가 사용자 발음을 가리지 않는다.
        assertThat(original).hasSize(1);
        assertThat(original.getFirst().reading()).isEqualTo("ねこ");
        assertThat(refreshed).hasSize(1);
        assertThat(refreshed.getFirst().reading()).isEqualTo("まお");
        assertThat(ruby.markup("猫")).isEqualTo("<ruby>猫<rt>まお</rt></ruby>");
    }
}
