package jp.co.translacat.novel;

import jp.co.translacat.novel.application.*;
import jp.co.translacat.novel.domain.*;
import jp.co.translacat.novel.infrastructure.persistence.NovelAudioAssetStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NovelAudioDurabilityTest {
    @TempDir Path temp;
    private final EpisodeKey key = new EpisodeKey("syosyetu", "n123aa", "1");
    private final NovelRepository translations = mock(NovelRepository.class);
    private final NovelQueryService query = mock(NovelQueryService.class);
    private final NovelPorts.Ai ai = mock(NovelPorts.Ai.class);
    private final MemoryObjects objects = new MemoryObjects();
    private FairAiScheduler scheduler;
    private NovelAudioAssetStore assets;
    private JdbcTemplate jdbc;
    private SourceEpisode source;

    @BeforeEach void prepare() {
        // 준비: 실제 SQL 상태 소유권과 가짜 객체 저장소를 사용하고 유료 provider는 관측 가능한 mock으로 둔다.
        var db = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("novel-audio-h2.sql")).execute(db);
        jdbc = new JdbcTemplate(db);
        assets = new NovelAudioAssetStore(jdbc);
        scheduler = new FairAiScheduler(2, 8);
        source = new SentenceSegmenter().segment(key, "題", List.of("猫。犬。"), null, null);
        when(query.requireCurrentRevision(eq(key), anyString())).thenReturn(source);
        when(ai.speechModel()).thenReturn("gpt-4o-mini-tts-2025-12-15");
        byte[] wav = new byte[44];
        wav[0] = 'R'; wav[1] = 'I'; wav[2] = 'F'; wav[3] = 'F';
        wav[8] = 'W'; wav[9] = 'A'; wav[10] = 'V'; wav[11] = 'E';
        when(ai.synthesize(anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(new NovelPorts.Speech(Base64.getEncoder().encodeToString(wav),
                        "audio/wav", 1.0, "openai", "gpt-4o-mini-tts-2025-12-15"));
    }

    @AfterEach void finish() { scheduler.close(); }


    @Test void processRestartAndUnchangedSentenceReuseDurableObjectWithoutAnotherSynthesis() {
        var first = service();
        String cat = source.segments().getFirst().id();
        String dog = source.segments().get(1).id();

        // 실행: 첫 생성 후 service를 새로 만들고 같은 객체 저장소와 DB로 재생한다.
        assertThat(first.synthesize(key, 7, source.revision(), cat, "ja", 0).cacheDisposition()).isEqualTo("MISS");
        assertThat(service().synthesize(key, 7, source.revision(), cat, "ja", 0).cacheDisposition()).isEqualTo("HIT");
        first.synthesize(key, 7, source.revision(), dog, "ja", 0);
        SourceEpisode edited = new SentenceSegmenter().segment(key, "題", List.of("虎。犬。"), null, null);
        when(query.requireCurrentRevision(key, edited.revision())).thenReturn(edited);
        assertThat(service().synthesize(key, 7, edited.revision(), edited.segments().get(1).id(), "ja", 0)
                .cacheDisposition()).isEqualTo("HIT");
        assertThat(service().synthesize(key, 7, edited.revision(), edited.segments().getFirst().id(), "ja", 0)
                .cacheDisposition()).isEqualTo("MISS");

        // 검증: 바뀐 첫 문장에만 새 합성이 있었고 다른 문장의 링크는 새 revision에서도 READY다.
        verify(ai, times(3)).synthesize(anyString(), anyString(), anyString(), eq("ja"), anyLong());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM novel_audio_asset WHERE state='READY'", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM novel_audio_segment_link", Integer.class)).isEqualTo(4);
    }

    @Test void failedUploadRetriesIdenticalBytesAndStorageFailureNeverBecomesCacheMiss() {
        objects.failNextPut.set(true);
        String segment = source.segments().getFirst().id();

        // 실행: TTS는 성공했지만 첫 저장이 실패한다. 다음 명시 재생에서 보관한 같은 바이트를 업로드한다.
        assertThatThrownBy(() -> service().synthesize(key, 7, source.revision(), segment, "ja", 0))
                .hasMessage("AUDIO_STORAGE_UNAVAILABLE");
        assertThat(jdbc.queryForObject("SELECT state FROM novel_audio_asset", String.class)).isEqualTo("UPLOAD_FAILED");
        assertThat(service().synthesize(key, 7, source.revision(), segment, "ja", 0).cacheDisposition())
                .isEqualTo("RECOVERED");
        objects.failReads.set(true);
        assertThatThrownBy(() -> service().synthesize(key, 7, source.revision(), segment, "ja", 0))
                .hasMessage("AUDIO_STORAGE_UNAVAILABLE");

        // 검증: 저장·조회 장애를 유료 cache miss로 바꾸지 않는다.
        verify(ai, times(1)).synthesize(anyString(), anyString(), anyString(), eq("ja"), anyLong());
    }

    private NovelAudioService service() {
        return new NovelAudioService(translations, query, ai, scheduler, assets, objects, temp.toString());
    }

    private static final class MemoryObjects implements NovelAudioObjectStore {
        private final Map<String, byte[]> content = new ConcurrentHashMap<>();
        private final AtomicBoolean failNextPut = new AtomicBoolean();
        private final AtomicBoolean failReads = new AtomicBoolean();

        @Override public void put(String key, Path file, String type, long bytes, String sha) {
            if (failNextPut.compareAndSet(true, false)) throw unavailable();
            try { content.put(key, Files.readAllBytes(file)); }
            catch (Exception failure) { throw unavailable(); }
        }

        @Override public ObjectInfo head(String key) {
            byte[] bytes = content.get(key);
            if (bytes == null) throw new Missing();
            return new ObjectInfo(bytes.length, sha(bytes), "audio/wav");
        }

        @Override public ObjectData read(String key, int maxBytes) {
            if (failReads.get()) throw unavailable();
            byte[] bytes = content.get(key);
            if (bytes == null) throw new Missing();
            return new ObjectData(bytes, "audio/wav");
        }

        private static NovelProblem unavailable() {
            return new NovelProblem("AUDIO_STORAGE_UNAVAILABLE", 503, true, 1000);
        }

        private static String sha(byte[] bytes) {
            try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
        }
    }
}
