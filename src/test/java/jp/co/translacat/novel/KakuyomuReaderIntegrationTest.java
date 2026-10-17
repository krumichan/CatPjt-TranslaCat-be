package jp.co.translacat.novel;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.infrastructure.japanese.FuriganaProcessor;
import jp.co.translacat.infrastructure.scraping.kakuyomu.parser.KakuyomuParser;
import jp.co.translacat.novel.application.*;
import jp.co.translacat.novel.domain.*;
import jp.co.translacat.novel.infrastructure.japanese.NovelRubyFormatter;
import jp.co.translacat.novel.infrastructure.persistence.NovelStore;
import jp.co.translacat.novel.infrastructure.source.KakuyomuSourceAdapter;
import jp.co.translacat.novel.infrastructure.source.NovelSourceRouter;
import jp.co.translacat.novel.infrastructure.source.SyosyetuSourceAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class KakuyomuReaderIntegrationTest {
    private static final String WORK = "11054822662896759076";
    private static final String EPISODE = "11054822662896857470";
    private final EpisodeKey key = new EpisodeKey("kakuyomu", WORK, EPISODE);

    @Test
    void opaqueIdsAndRoutingPreserveExistingSyosetuIdentity() {
        // 준비
        var syosetu = mock(SyosyetuSourceAdapter.class);
        var kakuyomu = mock(KakuyomuSourceAdapter.class);
        var router = new NovelSourceRouter(syosetu, kakuyomu);
        var legacy = new EpisodeKey("syosetu", "N123AA", "0");

        // 실행
        router.fetch(key);
        router.fetch(legacy);

        // 검증: 식별자를 숫자나 순번으로 바꾸지 않고 기존 별칭/단편 키도 그대로다.
        assertThat(key.value()).isEqualTo("kakuyomu:" + WORK + ":" + EPISODE);
        assertThat(key.sourceUrl()).isEqualTo("https://kakuyomu.jp/works/" + WORK + "/episodes/" + EPISODE);
        assertThat(legacy.value()).isEqualTo("syosyetu:n123aa:short");
        assertThat(legacy.sourceUrl()).isEqualTo("https://ncode.syosetu.com/n123aa/");
        verify(kakuyomu).fetch(key);
        verify(syosetu).fetch(legacy);
        assertThatThrownBy(() -> new EpisodeKey("kakuyomu", WORK, "short")).hasMessage("EPISODE_ID_INVALID");
        assertThatThrownBy(() -> new EpisodeKey("syosetu", WORK, EPISODE)).hasMessage("EPISODE_ID_INVALID");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ko", "en"})
    void parsedKakuyomuUsesProgressiveSharedDatabasePipelineAndDictionaryRevision(String target) throws Exception {
        // 준비: 실제 수집기 parser → 문장/revision → JDBC 저장소와 두 계정의 공통 번역 경로다.
        var sourcePort = new KakuyomuSourceAdapter(new KakuyomuParser(), NovelExecutionSettings.baseline());
        SourceEpisode source = sourcePort.parse(key, """
                <html><head><link rel="canonical" href="https://kakuyomu.jp/works/11054822662896759076/episodes/11054822662896857470"></head>
                <body><p class="widget-episodeTitle">猫の旅</p><div class="widget-episodeBody">
                <p>猫が来た。</p><p><ruby>遥<rt>はるか</rt></ruby>は笑った。</p></div></body></html>
                """);
        var data = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("novel-embedded-h2.sql")).execute(data);
        var jdbc = new JdbcTemplate(data);
        var store = new NovelStore(jdbc, new DataSourceTransactionManager(data), new ObjectMapper());
        store.saveSource(key, source, System.currentTimeMillis());
        var revision = new AtomicLong();
        var processor = mock(FuriganaProcessor.class);
        when(processor.dictionaryRevision()).thenAnswer(ignored -> revision.get());
        when(processor.convertToRuby(anyString())).thenAnswer(call -> ((String) call.getArgument(0))
                .replace("猫", "<ruby>猫<rt>" + (revision.get() == 0 ? "ねこ" : "ねこせんせい") + "</rt></ruby>"));
        var formatter = new NovelRubyFormatter(processor);
        var ai = new ControlledAi();
        var query = new NovelQueryService(store, ignored -> source, ai, 600, NovelExecutionSettings.baseline(), formatter);

        var commands = new NovelCommandService(store, query, ai, 10_000);
        try {
            // 실행: 계정 A의 첫 완성 문장이 저장된 동안 B도 같은 실행을 구독한다.
            var first = commands.start(key, 71, source.revision(), "first-" + target, false, target);
            assertThat(ai.prefix.await(5, TimeUnit.SECONDS)).isTrue();
            var second = commands.start(key, 72, source.revision(), "second-" + target, false, target);
            var displayed = query.status(key, 72, second.jobId());
            assertThat(displayed.completedSegments()).isEqualTo(1);
            assertThat(displayed.durableComplete()).isFalse();
            assertThat(displayed.pagerInfo().indexUrl()).isEqualTo(key.indexUrl());
            ai.release.countDown();
            var completed = await(query, second.jobId(), 72);
            var reused = query.reader(key, 73, target);

            // 검증: 원본/ID·권한·완료 판정을 유지하고 다른 계정 조회는 추가 AI 없이 저장본을 쓴다.
            assertThat(completed.durableComplete()).isTrue();
            assertThat(reused.durableComplete()).isTrue();
            assertThat(reused.jobId()).isNotEqualTo(first.jobId()).isNotEqualTo(second.jobId());
            assertThat(reused.segments()).extracting(ReaderSnapshot.Segment::translatedText)
                    .containsExactly(target + "-0", target + "-1");
            assertThat(ai.calls).hasValue(1);
            assertThat(store.byId(key, reused.jobId(), 73).providerCalls()).isZero();
            assertThatThrownBy(() -> query.status(key, 73, first.jobId())).hasMessage("JOB_NOT_FOUND");

            // 실행·검증: 사전 revision 변경은 화면 발음만 갱신하고 저장 원문/번역/캐시를 보존한다.
            revision.incrementAndGet();
            var dictionaryUpdated = query.reader(key, 73, target);
            assertThat(dictionaryUpdated.segments().getFirst().rubyTokens()).anySatisfy(token ->
                    assertThat(token.reading()).isEqualTo("ねこせんせい"));
            assertThat(dictionaryUpdated.segments().get(1).rubyTokens()).anySatisfy(token ->
                    assertThat(token.reading()).isEqualTo("はるか"));
            assertThat(dictionaryUpdated.revision()).isEqualTo(source.revision());
            assertThat(ai.calls).hasValue(1);
        } finally {
            ai.release.countDown();
            commands.close();
        }
    }

    private ReaderSnapshot await(NovelQueryService query, String job, long actor) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        ReaderSnapshot result;
        do {
            result = query.status(key, actor, job);
            if (result.durableComplete()) return result;
            Thread.sleep(10);
        } while (System.nanoTime() < end);
        assertThat(result.durableComplete()).isTrue();
        return result;
    }

    private static class ControlledAi implements NovelPorts.Ai {
        final AtomicInteger calls = new AtomicInteger();
        final CountDownLatch prefix = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        public String model() { return "controlled-openai-port"; }
        public String speechModel() { return "controlled-speech"; }
        public boolean progressiveAEnabled() { return true; }
        public NovelPorts.Translation translate(String trace, List<SourceEpisode.Segment> items, List<String> context, long left) {
            throw new AssertionError("Use the target-aware progressive contract");
        }
        public NovelPorts.Translation translateProgressive(String trace, String call, List<SourceEpisode.Segment> items,
                List<String> context, Map<String, String> glossary, long left, TranslationOptions.ResponseShape shape,
                TranslationOptions.ValidationPolicy validation, TranslationOptions.AnnotationPolicy annotation,
                BiConsumer<SourceEpisode.Segment, String> sentence, BiConsumer<SourceEpisode.Segment, String> failure, String target) {
            calls.incrementAndGet();
            var results = new LinkedHashMap<String, String>();
            for (var item : items) {
                results.put(item.id(), target + "-" + item.order());
                sentence.accept(item, results.get(item.id()));
                if (item.order() == 0) {
                    prefix.countDown();
                    try { assertThat(release.await(5, TimeUnit.SECONDS)).isTrue(); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(interrupted);
                    }
                }
            }
            return new NovelPorts.Translation(results, "OPENAI", model(), 10, 10);
        }
        public NovelPorts.Speech synthesize(String request, String text, String language, long left) {
            throw new AssertionError("Reader/translation/dictionary operations must not synthesize audio");
        }
    }
}
