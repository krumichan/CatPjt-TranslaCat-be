package jp.co.translacat.domain.novelgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.user.enums.RecentViewType;
import jp.co.translacat.domain.user.service.RecentViewService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NovelReaderRecentTargetTest {
    @Test void initialEnglishSourceVisitStoresTheTargetWithoutRelabelingKoreanAndPollsDoNotRepeatIt() throws Exception {
        // 준비: 영어 제목이 없는 원문 snapshot에도 마지막 방문 언어를 보존해야 한다.
        var embedded = mock(NovelEmbeddedService.class);
        var recent = mock(RecentViewService.class);
        var query = new NovelReaderQueryService(embedded, recent);
        var address = new NovelReaderAddress("syosyetu", "n123aa", "1");
        var snapshot = new ObjectMapper().readTree("{\"targetLanguage\":\"en\",\"title\":{\"rawJa\":\"猫\",\"ja\":\"猫\",\"ko\":null,\"translatedText\":null}}");
        when(embedded.reader(7, address, "en")).thenReturn(snapshot);
        when(embedded.status(7, address, "job")).thenReturn(snapshot);

        // 실행
        assertSame(snapshot, query.reader(7, address, "en"));
        query.status(7, address, "job");

        // 검증: poll은 저장 호출이 없고, 한국어 제목/영어 번역을 임의로 만들어 보내지 않는다.
        verify(recent).save(PlatformCode.SYOSETU, RecentViewType.EPISODE, "n123aa", "1", "猫", "猫", null, null, "en");
        verifyNoMoreInteractions(recent);
    }

    @Test void originalKoreanSourceRequestKeepsTheDefaultTargetAndShortStoryIdentity() throws Exception {
        // 준비
        var embedded = mock(NovelEmbeddedService.class);
        var recent = mock(RecentViewService.class);
        var query = new NovelReaderQueryService(embedded, recent);
        var address = new NovelReaderAddress("syosetu", "n123aa", "short");
        var snapshot = new ObjectMapper().readTree("{\"targetLanguage\":\"ko\",\"title\":{\"rawJa\":\"猫\",\"ja\":\"猫\",\"ko\":\"고양이\",\"translatedText\":\"고양이\"}}");
        when(embedded.reader(7, address)).thenReturn(snapshot);

        // 실행
        query.reader(7, address);

        // 검증
        verify(recent).save(PlatformCode.SYOSETU, RecentViewType.SHORT, "n123aa", null, "猫", "猫", "고양이", null, "ko");
        verifyNoMoreInteractions(recent);
    }
}
