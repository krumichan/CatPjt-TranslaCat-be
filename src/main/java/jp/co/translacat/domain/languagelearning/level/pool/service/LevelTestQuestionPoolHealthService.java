package jp.co.translacat.domain.languagelearning.level.pool.service;

import jp.co.translacat.domain.languagelearning.level.policy.LevelTestQuestionContentPolicy;
import jp.co.translacat.domain.languagelearning.level.pool.entity.LevelTestQuestionPool;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class LevelTestQuestionPoolHealthService {

    private final LevelTestQuestionPoolQueryService queryService;
    private final LevelTestQuestionPoolCommandService commandService;
    private final LevelTestQuestionContentPolicy contentPolicy;
    private final int scanSize;
    private final AtomicLong lastScannedId = new AtomicLong(0L);

    public LevelTestQuestionPoolHealthService(
            LevelTestQuestionPoolQueryService queryService,
            LevelTestQuestionPoolCommandService commandService,
            LevelTestQuestionContentPolicy contentPolicy,
            @Value("${language-learning.level-test.question-pool.health-scan-size:200}")
            int scanSize
    ) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.contentPolicy = contentPolicy;
        this.scanSize = Math.max(1, Math.min(1000, scanSize));
    }

    public synchronized SweepResult sweep() {
        long cursor = Math.max(0L, lastScannedId.get());
        List<LevelTestQuestionPool> targets = queryService.healthScanAfter(cursor, scanSize);
        if (targets.isEmpty() && cursor > 0L) {
            cursor = 0L;
            lastScannedId.set(0L);
            targets = queryService.healthScanAfter(0L, scanSize);
        }
        if (targets.isEmpty()) {
            return new SweepResult(0, 0, cursor);
        }

        int quarantined = 0;
        long lastId = cursor;
        for (LevelTestQuestionPool target : targets) {
            if (target.getId() != null) {
                lastId = Math.max(lastId, target.getId());
            }
            LevelTestQuestionContentPolicy.Health health = contentPolicy.inspect(target);
            if (health.valid()) {
                continue;
            }
            commandService.quarantine(target.getId(), health.reason());
            quarantined++;
            log.warn(
                    "Invalid Level Test pool question quarantined by health sweep. poolQuestionId={}, domain={}, itemType={}, reason={}",
                    target.getId(),
                    target.getDomain(),
                    target.getItemType(),
                    health.reason()
            );
        }
        lastScannedId.set(lastId);
        return new SweepResult(targets.size(), quarantined, lastId);
    }

    public record SweepResult(int scanned, int quarantined, Long lastScannedId) {
    }
}
