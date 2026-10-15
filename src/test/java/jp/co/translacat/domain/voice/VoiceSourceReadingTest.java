package jp.co.translacat.domain.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jp.co.translacat.domain.voice.enums.VoiceChannel;
import jp.co.translacat.domain.voice.enums.VoiceMode;
import jp.co.translacat.domain.voice.enums.VoiceSourceLanguageMode;
import jp.co.translacat.domain.voice.model.VoiceStreamContext;
import jp.co.translacat.domain.voice.service.VoiceSourceReadingService;
import jp.co.translacat.infrastructure.japanese.SudachiFuriganaProcessor;
import jp.co.translacat.infrastructure.japanese.SudachiFuriganaProcessor.ReadingToken;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class VoiceSourceReadingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static SudachiFuriganaProcessor realSudachi;

    @BeforeAll
    static void prepareDictionary() throws IOException {
        // 준비: 제품과 동일한 Sudachi 사전으로 실제 형태소 경계를 검증한다.
        realSudachi = new SudachiFuriganaProcessor();
        ReflectionTestUtils.setField(realSudachi, "dictPath", "src/main/resources/system_full.dic");
        realSudachi.init();
    }

    @Test
    void realSudachiPreservesUtf16SourceAndProducesJapaneseReadings() {
        // 준비: 선행·중간·끝 공백, 줄바꿈, 이모지, 기호와 오쿠리가나를 섞는다.
        String source = " 日本語😀\n食べる。 東京\t大阪 ";

        // 실행
        List<ReadingToken> tokens = realSudachi.tokenizeWithReadings(source);

        // 검증: 읽기 없는 원문 조각까지 이어 붙이면 입력이 정확히 복원된다.
        assertThat(tokens).isNotEmpty();
        assertThat(tokens.stream().map(ReadingToken::surface).reduce("", String::concat))
                .isEqualTo(source);
        assertThat(tokens).anySatisfy(token -> {
            assertThat(token.surface()).isEqualTo("東京");
            assertThat(token.reading()).isEqualTo("とうきょう");
        });
        assertThat(tokens).anySatisfy(token -> {
            assertThat(token.surface()).contains("😀");
            assertThat(token.reading()).isNull();
        });
        // 오쿠리가나가 포함된 형태소는 전체 표면형에 읽기를 붙인다.
        assertThat(tokens.stream().filter(token -> token.surface().contains("食")))
                .anySatisfy(token -> assertThat(token.reading()).isNotBlank());
    }

    @Test
    void localTokensCoverTheFullSourceAndOtherLanguagesUseNoSudachi() {
        // 준비
        var service = new VoiceSourceReadingService(realSudachi, MAPPER);
        String source = " 漢字 😀\n東京 ";

        // 실행
        ArrayNode japanese = service.forLanguage(source, "ja");
        ArrayNode english = service.forLanguage(source, "en");

        // 검증: 전송될 JSON 토큰에도 공백·이모지·줄바꿈이 그대로 남는다.
        StringBuilder reconstructed = new StringBuilder();
        japanese.forEach(token -> reconstructed.append(token.path("surface").asText()));
        assertThat(reconstructed.toString()).isEqualTo(source);
        assertThat(english).isEmpty();
    }

    @Test
    void sudachiFailureOrMissingSpanFallsBackToPlainSource() {
        // 준비: 로컬 분석 실패 및 손상된 토큰의 반환값을 각각 구성한다.
        var sudachi = mock(SudachiFuriganaProcessor.class);
        var service = new VoiceSourceReadingService(sudachi, MAPPER);
        when(sudachi.tokenizeWithReadings("日本語"))
                .thenThrow(new IllegalStateException("synthetic dictionary failure"));
        when(sudachi.tokenizeWithReadings("東京 大阪"))
                .thenReturn(List.of(new ReadingToken("東京", "とうきょう")));

        // 실행 및 검증: 빈 토큰은 FE가 원문을 그대로 표시하는 신호다.
        assertThat(service.forLanguage("日本語", "ja")).isEmpty();
        assertThat(service.forLanguage("東京 大阪", "ja")).isEmpty();
        verify(sudachi, times(2)).tokenizeWithReadings(anyString());
        assertThat(service.forLanguage("あ".repeat(3_001), "ja")).isEmpty();
        verifyNoMoreInteractions(sudachi);
    }

    @Test
    void confidentPerUtteranceLanguageOverridesOldChannelLock() {
        // 준비: 예전 채널 잠금은 영어지만 이번 발화는 일본어로 검출됐다.
        var service = new VoiceSourceReadingService(realSudachi, MAPPER);
        var context = context(null, "en");
        ObjectNode event = MAPPER.createObjectNode();
        event.put("sourceText", "東京です");
        event.put("detectedLanguage", "ja");
        event.put("languageConfidence", 0.92);
        event.put("lockedLanguage", "en");

        // 실행
        ArrayNode japanese = service.forFinal(context, event);
        event.put("detectedLanguage", "en");
        event.put("lockedLanguage", "ja");
        ArrayNode english = service.forFinal(context, event);

        // 검증: 발화별 언어 전환이 과거 잠금의 루비를 이어받지 않는다.
        assertThat(japanese).isNotEmpty();
        assertThat(english).isEmpty();
    }

    @Test
    void manualJapaneseAndSameLanguageTranslationStillGetReadings() {
        // 준비: 수동 일본어 입력과 일본어 목표 언어를 가진 세션이다.
        var service = new VoiceSourceReadingService(realSudachi, MAPPER);
        var context = new VoiceStreamContext(1L, "session", VoiceChannel.REMOTE, "connection",
                VoiceMode.MEDIA, VoiceSourceLanguageMode.MANUAL, "ja", "ja", "ja", "{}", 0);
        ObjectNode event = MAPPER.createObjectNode();
        event.put("sourceText", "東京です");
        event.put("detectedLanguage", "ja");
        event.put("languageConfidence", 0.9);
        event.put("lockedLanguage", "ja");

        // 실행 및 검증: 번역이 생략되는 동일 언어에서도 원문 읽기는 로컬에서 생성한다.
        assertThat(service.forFinal(context, event)).isNotEmpty();
    }

    @Test
    void explicitUnsupportedLanguageDoesNotBorrowOldJapaneseLock() {
        // 준비: 이전 발화의 채널 잠금은 일본어지만 현재 감지 언어는 비지원 언어다.
        var service = new VoiceSourceReadingService(realSudachi, MAPPER);
        ObjectNode event = MAPPER.createObjectNode();
        event.put("sourceText", "東京です");
        event.put("detectedLanguage", "zh");
        event.putNull("languageConfidence");
        event.put("lockedLanguage", "ja");

        // 실행 및 검증: 언어 실패 원문에 과거 잠금의 읽기를 붙이지 않는다.
        assertThat(service.forFinal(context(null, "ja"), event)).isEmpty();
    }

    @Test
    void validatedStoredReadingsSurviveTemporaryAnalysisFailure() {
        // 준비: 먼저 저장한 읽기가 원문을 완전하게 덮는 경우와 빠진 경우를 구분한다.
        var service = new VoiceSourceReadingService(realSudachi, MAPPER);
        String stored = "[{\"surface\":\"東京\",\"reading\":\"とうきょう\"},"
                + "{\"surface\":\" 大阪\",\"reading\":null}]";

        // 실행 및 검증: 원문 일치만 재사용하고 불일치·손상 데이터는 빈 목록으로 처리한다.
        assertThat(service.preserveStoredWhenGenerationFails("[]", stored, "東京 大阪"))
                .isEqualTo(stored);
        assertThat(service.preserveStoredWhenGenerationFails("[]", stored, "東京 京都"))
                .isEqualTo("[]");
        assertThat(service.preserveStoredWhenGenerationFails("[]", "invalid", "東京 大阪"))
                .isEqualTo("[]");
    }

    @Test
    void concurrentVoiceAndExistingRubyCallsDoNotMixTokenizerState() {
        // 준비: 동일한 Sudachi 인스턴스에 Voice와 기존 HTML 루비 요청을 교차로 보낸다.
        var pool = Executors.newFixedThreadPool(4);
        try {
            List<String> sources = List.of("東京に行く", "大阪で食べる", "日本語😀 学ぶ", "京都\n東京");
            List<CompletableFuture<String>> requests = java.util.stream.IntStream.range(0, 24)
                    .mapToObj(index -> CompletableFuture.supplyAsync(() -> {
                        if (index % 3 == 0) {
                            return realSudachi.convertToRuby("食べる東京");
                        }
                        String source = sources.get(index % sources.size());
                        return realSudachi.tokenizeWithReadings(source).stream()
                                .map(ReadingToken::surface)
                                .reduce("", String::concat);
                    }, pool))
                    .toList();

            // 실행
            List<String> results = requests.stream().map(CompletableFuture::join).toList();

            // 검증: 공유 lattice에서 다른 발화의 표면형이 섞이지 않는다.
            for (int index = 0; index < results.size(); index++) {
                if (index % 3 == 0) {
                    assertThat(results.get(index)).contains("東京");
                } else {
                    assertThat(results.get(index)).isEqualTo(sources.get(index % sources.size()));
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private VoiceStreamContext context(String manualLanguage, String lockedLanguage) {
        return new VoiceStreamContext(1L, "session", VoiceChannel.REMOTE, "connection",
                VoiceMode.MEDIA, VoiceSourceLanguageMode.AUTO, manualLanguage, lockedLanguage,
                "ko", "{}", 0);
    }
}
