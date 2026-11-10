package jp.co.translacat.domain.accountbook.analysis.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import jp.co.translacat.domain.accountbook.analysis.dto.SpendingAnalysisDtos.PeriodType;
import jp.co.translacat.domain.accountbook.analysis.dto.SpendingAnalysisDtos.Request;
import jp.co.translacat.domain.accountbook.analysis.repository.SpendingAnalysisJobStore;
import jp.co.translacat.domain.accountbook.analysis.repository.SpendingAnalysisJobStore.Job;
import jp.co.translacat.domain.accountbook.analysis.repository.SpendingAnalysisJobStore.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "ANALYSIS_JOB_TEST_MYSQL_PORT", matches = "[0-9]+")
class SpendingAnalysisJobMysqlTest {
    @Test
    void uniqueActiveSlotSurvivesConcurrentRequestsAndCancellationUntilWorkerFinishes() throws Exception {
        // 준비: 이 테스트 전용 MySQL 스키마에 실제 마이그레이션을 적용한다.
        String port = System.getenv("ANALYSIS_JOB_TEST_MYSQL_PORT");
        var source = new DriverManagerDataSource("jdbc:mysql://127.0.0.1:" + port
                + "/analysis_jobs_test?allowPublicKeyRetrieval=true&sslMode=DISABLED",
                "analysis_test", "analysis-test-only");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE account_book (id BIGINT PRIMARY KEY)");
        jdbc.update("INSERT INTO account_book VALUES (1), (2)");
        var migration = new ResourceDatabasePopulator(new FileSystemResource(
                "scripts/sql/20261110_account_book_spending_analysis_jobs.sql"));
        migration.execute(source);
        migration.execute(source);
        var store = new SpendingAnalysisJobStore(jdbc, JsonMapper.builder().findAndAddModules().build());
        var request = new Request(PeriodType.YEARLY, 2026, null, "ko", "테스트 설명");
        var range = SpendingAnalysisPeriods.resolve(request);

        // 실행: 다른 요청자가 같은 가계부로 동시에 연간 분석을 제출한다.
        var ready = new CountDownLatch(2);
        var go = new CountDownLatch(1);
        Job active;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> raceInsert(store, request, range, 101, ready, go));
            var second = executor.submit(() -> raceInsert(store, request, range, 102, ready, go));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            Job left = first.get(10, TimeUnit.SECONDS);
            Job right = second.get(10, TimeUnit.SECONDS);
            assertThat(left == null ^ right == null).isTrue();
            active = left != null ? left : right;
        }
        assertThat(store.active(1)).contains(active);
        assertThat(store.insert(2, 101, request, range, null).status()).isEqualTo(Status.QUEUED);

        // 검증: 취소 중에는 새 실행이 막히고, 늦은 완료가 취소를 덮어쓰지 않는다.
        assertThat(store.claim(active.id(), Instant.now().plusSeconds(600))).isTrue();
        assertThat(store.claim(active.id(), Instant.now().plusSeconds(600))).isFalse();
        assertThat(store.request(active.id())).isEqualTo(request);
        store.cancel(active.id());
        assertThat(store.active(1)).get().extracting(Job::status).isEqualTo(Status.CANCELING);
        assertThat(store.request(active.id())).isNull();
        assertThat(store.complete(active.id(), "discarded", Instant.now())).isFalse();
        assertThatThrownBy(() -> store.insert(1, 102, request, range, null)).isInstanceOf(DuplicateKeyException.class);
        store.finishWithoutResult(active.id(), "SPENDING_ANALYSIS_FAILED");
        assertThat(store.find(1, active.id())).get().extracting(Job::status).isEqualTo(Status.CANCELED);

        // 검증: 저장 트랜잭션이 실패하면 완료 표시가 되돌아가고 만료 시 슬롯이 풀린다.
        var retry = store.insert(1, 102, request, range, Instant.now().plusSeconds(5));
        var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        tx.executeWithoutResult(transaction -> {
            assertThat(store.complete(retry.id(), "rolled-back", Instant.now())).isTrue();
            transaction.setRollbackOnly();
        });
        assertThat(store.active(1)).contains(retry);
        store.expire(Instant.now().plusSeconds(6));
        assertThat(store.active(1)).isEmpty();
        assertThat(store.find(1, retry.id())).get().extracting(Job::status).isEqualTo(Status.FAILED);

        // 검증: 가계부를 물리 삭제하면 활성 작업과 완료 메타데이터도 정리된다.
        jdbc.update("DELETE FROM account_book WHERE id IN (1, 2)");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account_book_spending_analysis_job", Long.class)).isZero();
    }

    private Job raceInsert(SpendingAnalysisJobStore store, Request request, SpendingAnalysisPeriods.Range range,
                           long requester, CountDownLatch ready, CountDownLatch go) throws InterruptedException {
        ready.countDown();
        if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent test did not start");
        try {
            return store.insert(1, requester, request, range, null);
        } catch (DuplicateKeyException duplicate) {
            return null;
        }
    }
}
