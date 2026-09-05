package jp.co.translacat.domain.languagelearning.level.pool.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "language-learning.level-test.question-pool",
        name = "replenish-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class LevelTestQuestionPoolReplenishmentScheduler {

    private final LevelTestQuestionPoolReplenishmentService replenishmentService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(
            fixedDelayString = "${language-learning.level-test.question-pool.replenish-fixed-delay-ms:600000}",
            initialDelayString = "${language-learning.level-test.question-pool.replenish-initial-delay-ms:60000}"
    )
    public void replenish() {
        if (!running.compareAndSet(false, true)) {
            log.info("Level Test pool replenishment skipped because previous run is active.");
            return;
        }

        long startedAt = System.nanoTime();
        try {
            var result = replenishmentService.replenish();
            log.info(
                    "Level Test pool maintenance completed. target={}, requested={}, succeeded={}, failed={}, healthScanned={}, quarantined={}, repairRequested={}, repaired={}, generationRequested={}, generated={}, elapsedMs={}",
                    result.targetSize(),
                    result.requested(),
                    result.succeeded(),
                    result.failed(),
                    result.healthScanned(),
                    result.quarantined(),
                    result.repairRequested(),
                    result.repaired(),
                    result.generationRequested(),
                    result.generated(),
                    (System.nanoTime() - startedAt) / 1_000_000L
            );
        } catch (RuntimeException exception) {
            log.error("Level Test pool replenishment scheduler failed.", exception);
        } finally {
            running.set(false);
        }
    }
}
