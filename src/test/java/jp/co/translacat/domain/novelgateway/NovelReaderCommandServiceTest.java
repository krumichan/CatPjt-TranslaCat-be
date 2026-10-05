package jp.co.translacat.domain.novelgateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class NovelReaderCommandServiceTest {
    @Test
    void repairForwardsOnlyValidatedScopeAndKeepsActorServerOwned() {
        // 준비
        var embedded = mock(NovelEmbeddedService.class);
        var service = new NovelReaderCommandService(embedded);
        var address = new NovelReaderAddress("syosyetu", "n123aa", "1");
        var request = new NovelReaderCommandService.RepairRequest("revision", "repair-request", "segment-1");

        // 실행 및 검증
        service.repair(12L, address, "job_1", request);
        verify(embedded).repair(12L, address, "job_1", request);
        assertThatThrownBy(() -> service.repair(12L, address, "../job", request)).hasMessage("NOVEL_REQUEST_INVALID");
        assertThatThrownBy(() -> new NovelReaderCommandService.RepairRequest("revision", "key", "../segment"))
                .hasMessage("NOVEL_REQUEST_INVALID");
    }

    @Test
    void cancellationPreservesActorAndEpisodeAndRejectsPathInjection() {
        // 준비
        var embedded = mock(NovelEmbeddedService.class);
        var service = new NovelReaderCommandService(embedded);
        var address = new NovelReaderAddress("syosyetu", "n123aa", "1");

        // 실행 및 검증: 요청 body에서 사용자나 작업 주소를 받지 않는다.
        for (String invalid : new String[]{null, "", "../job", "job/cancel", "job?actor=2"}) {
            assertThatThrownBy(() -> service.cancel(12L, address, invalid)).hasMessage("NOVEL_REQUEST_INVALID");
        }
        verifyNoInteractions(embedded);
        service.cancel(12L, address, "job_1");
        verify(embedded).cancel(12L, address, "job_1");
    }
}
