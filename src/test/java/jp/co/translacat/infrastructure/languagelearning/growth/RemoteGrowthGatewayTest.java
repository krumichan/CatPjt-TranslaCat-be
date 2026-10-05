package jp.co.translacat.infrastructure.languagelearning.growth;

import jp.co.translacat.domain.languagelearning.growth.model.GrowthActivitySnapshot;
import jp.co.translacat.domain.languagelearning.growth.model.GrowthSnapshot;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import org.junit.jupiter.api.Test;

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
    void requiredRemoteClientCannotBeOmittedAtStartup() {
        // 준비 / 실행: 서비스 OFF 대신 누락된 필수 의존성을 생성 시점에 거부한다.
        var failure = assertThrows(NullPointerException.class, () -> new RemoteGrowthGateway(null));

        // 검증
        assertEquals("Growth HTTP client is required.", failure.getMessage());
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
        return new RemoteGrowthGateway(client);
    }
}
