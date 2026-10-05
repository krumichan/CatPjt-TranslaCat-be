package jp.co.translacat.domain.novelgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.novel.application.CatalogFacade;
import jp.co.translacat.novel.application.NovelAudioService;
import jp.co.translacat.novel.application.NovelCommandService;
import jp.co.translacat.novel.application.NovelFacade;
import jp.co.translacat.novel.application.NovelQueryService;
import jp.co.translacat.novel.application.ReaderSnapshot;
import jp.co.translacat.novel.domain.EpisodeKey;
import jp.co.translacat.novel.domain.SentenceSegmenter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NovelEmbeddedAvailabilityTest {
    @Test
    void embeddedReaderWorksWithoutFeatureFlagAndDoesNotStartTranslationOrAudio() {
        // 준비: 같은 JVM의 실제 facade를 사용하며 원문 조회만 합성한다.
        var query = mock(NovelQueryService.class);
        var commands = mock(NovelCommandService.class);
        var audio = mock(NovelAudioService.class);
        var catalog = mock(CatalogFacade.class);
        var key = new EpisodeKey("syosyetu", "n123ab", "1");
        var source = new SentenceSegmenter().segment(key, "題", List.of("猫が歩いた。"), null, null);
        when(query.reader(key, 73L)).thenReturn(ReaderSnapshot.of(key, source, null));
        var embedded = new NovelEmbeddedService(new NovelFacade(query, commands, audio), catalog,
                new ObjectMapper());

        // 실행: 활성화 속성이나 독립 NOVEL 주소 없이 내장 reader에 접근한다.
        var response = embedded.reader(73L, new NovelReaderAddress("syosyetu", "n123ab", "1"));

        // 검증: disabled 503 대신 원문을 반환하며 본문 열기가 번역/TTS를 선실행하지 않는다.
        assertThat(response.path("state").asText()).isEqualTo("SOURCE_READY");
        assertThat(response.path("segments").size()).isPositive();
        verify(query).reader(key, 73L);
        verifyNoInteractions(commands, audio, catalog);
    }
}
