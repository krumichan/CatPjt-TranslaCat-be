package jp.co.translacat.domain.accountbook.analysis.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import jp.co.translacat.domain.accountbook.accountbook.service.AccountBookAccessService;
import jp.co.translacat.domain.accountbook.analysis.dto.SpendingAnalysisDtos.*;
import jp.co.translacat.domain.accountbook.analysis.repository.JdbcSpendingAnalysisHistoryStore;
import jp.co.translacat.domain.accountbook.analysis.repository.SpendingAnalysisHistoryStore;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpendingAnalysisHistoryTest {
    private Response response(String id, Instant generatedAt, String comment) {
        return new Response(PeriodType.MONTHLY, 2026, 10, LocalDate.of(2026,10,1), LocalDate.of(2026,11,1),
                "JPY", 0, "12000", 2, new Coverage(2,2,false,null), "당시 거래 근거와 코멘트를 반영한 분석 결과입니다.",
                List.of(), comment, generatedAt, "snapshot-at-the-time", id, null, null, "purchase-context-v3", null);
    }

    @Test
    void samePeriodReanalysisCreatesIndependentSnapshotsAndSurvivesStoreRecreation() {
        // 준비: 실제 H2 저장소에 같은 기간의 두 결과를 순서대로 저장한다.
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE account_book_spending_analysis(id VARCHAR(36) PRIMARY KEY,account_book_id BIGINT,created_by BIGINT,generated_at TIMESTAMP(6),result_json CLOB)");
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var store = new JdbcSpendingAnalysisHistoryStore(jdbc, mapper);
        var first = response(UUID.randomUUID().toString(), Instant.parse("2026-11-05T00:00:00Z"), null);
        var second = response(UUID.randomUUID().toString(), Instant.parse("2026-11-05T00:01:00Z"), "선물입니다.");

        // 실행: 저장소 객체를 다시 만들어도 기록은 DB에서 읽힌다.
        store.save(1L, 7L, first);
        store.save(1L, 7L, second);
        var reopened = new JdbcSpendingAnalysisHistoryStore(jdbc, mapper);

        // 검증: 같은 기간의 재분석이 첫 결과·코멘트를 덮어쓰지 않는다.
        assertThat(reopened.count(1L)).isEqualTo(2);
        assertThat(reopened.list(1L, 0, 10)).extracting(Response::analysisId).containsExactly(second.analysisId(), first.analysisId());
        assertThat(reopened.find(1L, first.analysisId()).orElseThrow().contextComment()).isNull();
        assertThat(reopened.find(1L, second.analysisId()).orElseThrow().contextComment()).isEqualTo("선물입니다.");
        assertThat(reopened.find(2L, first.analysisId())).isEmpty();
    }

    @Test
    void historyIsNeverReadBeforeAccountBookAccessCheck() {
        // 준비
        var access = mock(AccountBookAccessService.class);
        var store = mock(SpendingAnalysisHistoryStore.class);
        when(access.getAccessibleAccountBook(3L, 7L)).thenThrow(new IllegalArgumentException("No access"));
        var history = new SpendingAnalysisHistoryService(access, store);

        // 실행 / 검증
        assertThatThrownBy(() -> history.list(3L, 7L, 0, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> history.find(3L, 7L, UUID.randomUUID().toString())).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(store);
    }

    @Test
    void previousAdviceDoesNotLeakLaterOrOverlappingPeriodsIntoPastAnalysis() {
        // 준비
        var access = mock(AccountBookAccessService.class);
        var store = mock(SpendingAnalysisHistoryStore.class);
        when(store.list(1L,0,50)).thenReturn(List.of(response(UUID.randomUUID().toString(),Instant.now(),null)));
        var history = new SpendingAnalysisHistoryService(access,store);

        // 실행 / 검증: 10월 결과를 9월 분석의 문맥으로 사용하지 않는다.
        assertThat(history.previousAdvice(1L,7L,LocalDate.of(2026,9,1))).isEmpty();
    }
}
