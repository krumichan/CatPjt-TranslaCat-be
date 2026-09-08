package jp.co.translacat.batch.languagelearning.level;

import jp.co.translacat.domain.languagelearning.level.pool.service.LevelTestQuestionPoolReplenishmentService;
import jp.co.translacat.domain.languagelearning.setting.service.LanguageLearningAdminSettingQueryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class LevelTestQuestionPoolReplenishmentBatch {

    private final LevelTestQuestionPoolReplenishmentService replenishmentService;
    private final LanguageLearningAdminSettingQueryService adminSettingQueryService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(
            fixedDelayString = "${language-learning.level-test.question-pool.replenish-fixed-delay-ms:600000}",
            initialDelayString = "${language-learning.level-test.question-pool.replenish-initial-delay-ms:60000}"
    )
    public void replenish() {
        if (!adminSettingQueryService
                .isLevelTestQuestionPoolReplenishmentEnabled()) {
            log.debug(
                    "Level Test pool replenishment skipped because admin setting is disabled."
            );
            return;
        }

        if (!running.compareAndSet(false, true)) {
            log.info(
                    "Level Test pool replenishment skipped because previous run is active."
            );
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
            log.error(
                    "Level Test pool replenishment batch failed.",
                    exception
            );
        } finally {
            running.set(false);
        }
    }
}
