package jp.co.translacat.infrastructure.languagelearning.growth;

import jp.co.translacat.domain.languagelearning.growth.model.GrowthActivitySnapshot;
import jp.co.translacat.domain.languagelearning.growth.model.GrowthSnapshot;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RemoteGrowthGatewayTest {
    @Test
    void currentSnapshotUsesOnlyRemoteClientWithoutCoreDatabase() {
        // 준비: DataSource나 이관 저장소 없이 현재 조회 client만 조립한다.
        var client = mock(GrowthHttpClient.class);
        var expected = new GrowthSnapshot(123, null, List.of(), Map.of());
        when(client.snapshot(123, List.of("key"))).thenReturn(expected);

        // 실행
        var actual = gateway(client).snapshot(123L, List.of("key"));

        // 검증
        assertSame(expected, actual);
        verify(client).snapshot(123, List.of("key"));
        verifyNoMoreInteractions(client);
    }

    @Test
    void disabledReadStartsWithoutClientAndFailsExplicitly() {
        // 준비
        var gateway = new RemoteGrowthGateway(new GrowthProperties(),
                new StaticListableBeanFactory().getBeanProvider(GrowthHttpClient.class));

        // 실행
        var failure = assertThrows(LanguageLearningServiceException.class, () -> gateway.snapshot(123L));

        // 검증
        assertEquals("LL_GROWTH_DISABLED", failure.getErrorCode());
    }

    @Test
    void changedRevisionDoesNotReturnMixedPages() {
        // 준비: 현재 LL 결과가 페이지 사이에서 변경된 상태를 구성한다.
        var client = mock(GrowthHttpClient.class);
        var activity = mock(GrowthActivitySnapshot.class);
        var from = LocalDate.parse("2026-09-01");
        var to = LocalDate.parse("2026-09-27");
        when(client.activities(123, null, from, to, 0)).thenReturn(
                new GrowthHttpClient.ActivityPage(123, List.of(activity), 5L, "1"));
        when(client.activities(123, null, from, to, 5)).thenReturn(
                new GrowthHttpClient.ActivityPage(123, List.of(), null, "2"));

        // 실행
        var failure = assertThrows(LanguageLearningServiceException.class,
                () -> gateway(client).activities(123L, null, from, to));

        // 검증
        assertEquals("GROWTH_READ_CHANGED", failure.getErrorCode());
    }

    @Test
    void cursorMustAdvanceAcrossCurrentPages() {
        // 준비
        var client = mock(GrowthHttpClient.class);
        var date = LocalDate.parse("2026-09-27");
        when(client.activities(123, null, date, date, 0)).thenReturn(
                new GrowthHttpClient.ActivityPage(123, List.of(mock(GrowthActivitySnapshot.class)), 0L, "1"));

        // 실행 및 검증
        assertThrows(IllegalStateException.class, () -> gateway(client).activities(123L, null, date, date));
        verify(client, times(1)).activities(123, null, date, date, 0);
    }

    private RemoteGrowthGateway gateway(GrowthHttpClient client) {
        var properties = new GrowthProperties();
        properties.setEnabled(true);
        var beans = new StaticListableBeanFactory();
        beans.addBean("growthHttpClient", client);
        return new RemoteGrowthGateway(properties, beans.getBeanProvider(GrowthHttpClient.class));
    }
}
