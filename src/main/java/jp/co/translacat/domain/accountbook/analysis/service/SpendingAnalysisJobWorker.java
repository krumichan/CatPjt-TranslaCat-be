package jp.co.translacat.domain.accountbook.analysis.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class SpendingAnalysisJobWorker {
    private static final int WORKERS = 2;

    private final SpendingAnalysisJobService jobs;
    private final ExecutorService executor = new ThreadPoolExecutor(
            WORKERS, WORKERS, 0, TimeUnit.SECONDS, new SynchronousQueue<>());

    @Scheduled(fixedDelayString = "${account-book.spending-analysis.poll-delay-ms:2000}")
    public void poll() {
        // 긴 호출은 별도 워커가 실행하며 메모리 큐 대신 DB에만 대기 작업을 둔다.
        jobs.expire();
        for (int index = 0; index < WORKERS; index++) {
            try {
                executor.execute(jobs::runNext);
            } catch (RejectedExecutionException busy) {
                break;
            }
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
