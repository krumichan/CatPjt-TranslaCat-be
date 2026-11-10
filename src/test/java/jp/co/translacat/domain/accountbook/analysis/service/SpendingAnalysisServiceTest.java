package jp.co.translacat.domain.accountbook.analysis.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import jp.co.translacat.domain.accountbook.analysis.dto.SpendingAnalysisDtos.*;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookTransactionResponseDto;
import jp.co.translacat.infrastructure.client.ai.server.AiServerClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpendingAnalysisServiceTest {
    final SpendingAnalysisSnapshotService snapshots = mock(SpendingAnalysisSnapshotService.class);
    final AiServerClient ai = mock(AiServerClient.class);
    final SpendingAnalysisHistoryService history = mock(SpendingAnalysisHistoryService.class);
    final SpendingAnalysisService service = new SpendingAnalysisService(snapshots, ai, history);
    final Request request = new Request(PeriodType.MONTHLY, 2026, 10, "ko", "필수 약 구입");

    private SpendingAnalysisSnapshotService.Prepared prepared(long count) throws Exception {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var transaction = mapper.readValue("""
                {"id":91,"accountBookId":1,"type":"EXPENSE","title":"필수 약 구입",
                 "storeName":"Pharmacy","category":"생활","amount":"9007199254740993.125",
                 "transactionDate":"2026-10-12","memo":"처방약"}
                """, AccountBookTransactionResponseDto.class);
        var input = new AiRequest(PeriodType.MONTHLY, 2026, 10, "ko", "KWD", 3,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1), transaction.amount().toPlainString(), count,
                new Coverage(count, count, false, null), null, request.comment(), List.of());
        return new SpendingAnalysisSnapshotService.Prepared(input, Map.of("tx-1", transaction), "fingerprint");
    }

    private AiResponse generated(List<String> refs) {
        return new AiResponse("거래 목적을 확인하고 조정할 지출을 검토하세요.", List.of(
                new AiFinding("약 구입 확인", "10월 12일 필수 약 구입 거래는 처방약 메모가 있어 필수 지출로 보입니다.",
                        "필수 약은 유지하고 중복 구매만 확인하세요.", null, "복용 주기는 알 수 없습니다.", refs)));
    }

    @Test
    void evidenceIsEnrichedFromAuthorizedSnapshotAndMoneyRemainsExact() throws Exception {
        // 준비
        var prepared = prepared(1);
        when(snapshots.prepare(1L, 2L, request)).thenReturn(prepared);
        when(ai.callSpendingAnalysis(prepared.input())).thenReturn(generated(List.of("tx-1")));
        when(snapshots.current(1L, 2L, PeriodType.MONTHLY, 2026, 10))
                .thenReturn(new SnapshotInfo("fingerprint", 1, prepared.input().totalExpense()));

        // 실행
        var response = service.analyze(1L, 2L, request);

        // 검증: 공급자가 금액이나 임의 거래를 응답에 삽입할 수 없다.
        assertThat(response.findings()).singleElement().satisfies(item -> {
            assertThat(item.evidenceAmount()).isEqualTo("9007199254740993.125");
            assertThat(item.evidenceTransactions()).extracting(AccountBookTransactionResponseDto::id).containsExactly(91L);
        });
        assertThat(response.contextComment()).isEqualTo(request.comment());
        verify(ai, times(1)).callSpendingAnalysis(prepared.input());
    }

    @Test
    void storesMoreThanEightFindingsWhenTheAiResponseFitsTheNewLimit() throws Exception {
        // 준비: BE에서 기존 8개 상한을 넘는 월별 응답의 근거와 저장 경로를 검사한다.
        var prepared = prepared(1);
        when(snapshots.prepare(1L, 2L, request)).thenReturn(prepared);
        var findings = IntStream.range(0, 24)
                .mapToObj(index -> new AiFinding("검토할 선택 " + index,
                        "선택 기간의 실제 거래 내역을 근거로 다음번에 조정할 여지를 설명합니다.",
                        "다음 구매에서 실제 필요를 유지하며 대안을 비교하세요.", null, null, List.of("tx-1")))
                .toList();
        when(ai.callSpendingAnalysis(prepared.input()))
                .thenReturn(new AiResponse("실제 거래를 근거로 정리한 월별 결과입니다.", findings, "recurring-choice-v6", null));
        when(snapshots.current(1L, 2L, PeriodType.MONTHLY, 2026, 10))
                .thenReturn(new SnapshotInfo("fingerprint", 1, prepared.input().totalExpense()));

        // 실행 / 검증
        var response = service.analyze(1L, 2L, request);
        assertThat(response.findings()).hasSize(24);
        assertThat(response.findings().getLast().evidenceTransactions()).hasSize(1);
        verify(history).save(1L, 2L, response);
    }

    @Test
    void unknownDuplicateAndNullEvidenceReferencesAreRejected() throws Exception {
        // 준비
        var prepared = prepared(1);
        when(snapshots.prepare(1L, 2L, request)).thenReturn(prepared);
        for (var refs : List.of(List.of("tx-999"), List.of("tx-1", "tx-1"), Arrays.asList((String) null))) {
            when(ai.callSpendingAnalysis(prepared.input())).thenReturn(generated(refs));

            // 실행 / 검증
            assertThatThrownBy(() -> service.analyze(1L, 2L, request))
                    .isInstanceOfSatisfying(ResponseStatusException.class, error ->
                            assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
        }
    }

    @Test
    void changedSnapshotDiscardsPaidResultWithoutRetry() throws Exception {
        // 준비
        var prepared = prepared(1);
        when(snapshots.prepare(1L, 2L, request)).thenReturn(prepared);
        when(ai.callSpendingAnalysis(prepared.input())).thenReturn(generated(List.of("tx-1")));
        when(snapshots.current(1L, 2L, PeriodType.MONTHLY, 2026, 10)).thenReturn(new SnapshotInfo("changed", 1, "100"));

        // 실행 / 검증
        assertThatThrownBy(() -> service.analyze(1L, 2L, request)).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(error.getReason()).isEqualTo("STALE_SNAPSHOT");
                });
        verify(ai, times(1)).callSpendingAnalysis(prepared.input());
    }

    @Test
    void accessFailureAndEmptyPeriodNeverCallProvider() throws Exception {
        // 준비: 먼저 권한 실패를 확인한다.
        when(snapshots.prepare(1L, 2L, request)).thenThrow(new IllegalArgumentException("No access"));

        // 실행 / 검증
        assertThatThrownBy(() -> service.analyze(1L, 2L, request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(ai);

        // 준비: 빈 기간은 정상 빈 결과로 반환한다.
        reset(snapshots);
        when(snapshots.prepare(1L, 2L, request)).thenReturn(prepared(0));

        // 실행 / 검증
        assertThat(service.analyze(1L, 2L, request).findings()).isEmpty();
        verifyNoInteractions(ai);
    }

    @Test
    void providerFailureIsNotRetriedAndBlankGeneratedTextIsRejected() throws Exception {
        // 준비
        var prepared = prepared(1);
        when(snapshots.prepare(1L, 2L, request)).thenReturn(prepared);
        when(ai.callSpendingAnalysis(prepared.input())).thenThrow(new RuntimeException("provider unavailable"));

        // 실행 / 검증
        assertThatThrownBy(() -> service.analyze(1L, 2L, request)).isInstanceOf(ResponseStatusException.class);
        verify(ai, times(1)).callSpendingAnalysis(prepared.input());

        // 준비 / 실행 / 검증: 구조만 맞는 빈 문자열도 유효한 분석으로 취급하지 않는다.
        reset(ai);
        when(ai.callSpendingAnalysis(prepared.input())).thenReturn(new AiResponse(" ", List.of()));
        assertThatThrownBy(() -> service.analyze(1L, 2L, request)).isInstanceOf(ResponseStatusException.class);
    }
}
