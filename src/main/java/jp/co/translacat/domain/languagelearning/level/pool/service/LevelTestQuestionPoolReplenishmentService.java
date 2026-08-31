package jp.co.translacat.domain.languagelearning.level.pool.service;

import jp.co.translacat.domain.languagelearning.level.entity.LevelTestSession;
import jp.co.translacat.domain.languagelearning.level.policy.LevelTestRecipe;
import jp.co.translacat.domain.languagelearning.level.pool.audio.service.LevelTestReferenceAudioUploadService;
import jp.co.translacat.domain.languagelearning.level.pool.entity.LevelTestQuestionPool;
import jp.co.translacat.domain.languagelearning.level.pool.policy.LevelTestQuestionPoolPolicy;
import jp.co.translacat.domain.languagelearning.level.pool.policy.LevelTestQuestionPoolTargetPlanner;
import jp.co.translacat.domain.languagelearning.level.pool.support.LevelTestPoolGenerationRejectedException;
import jp.co.translacat.domain.languagelearning.level.service.LevelTestQuestionService;
import jp.co.translacat.domain.languagelearning.quality.dto.DiversityContext;
import jp.co.translacat.domain.languagelearning.setting.entity.LanguageLearningUserSetting;
import jp.co.translacat.domain.languagelearning.setting.repository.LanguageLearningUserSettingRepository;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class LevelTestQuestionPoolReplenishmentService {

    private final LanguageLearningUserSettingRepository userSettingRepository;
    private final LevelTestQuestionPoolQueryService poolQueryService;
    private final LevelTestQuestionPoolCommandService poolCommandService;
    private final LevelTestQuestionPoolHealthService healthService;
    private final LevelTestQuestionPoolTargetPlanner targetPlanner;
    private final LevelTestQuestionPoolPolicy poolPolicy;
    private final LevelTestQuestionService questionService;
    private final LevelTestReferenceAudioUploadService audioUploadService;
    private final Executor executor;
    private final int batchSize;
    private final int repairBatchSize;

    public LevelTestQuestionPoolReplenishmentService(
            LanguageLearningUserSettingRepository userSettingRepository,
            LevelTestQuestionPoolQueryService poolQueryService,
            LevelTestQuestionPoolCommandService poolCommandService,
            LevelTestQuestionPoolHealthService healthService,
            LevelTestQuestionPoolTargetPlanner targetPlanner,
            LevelTestQuestionPoolPolicy poolPolicy,
            LevelTestQuestionService questionService,
            LevelTestReferenceAudioUploadService audioUploadService,
            @Qualifier("levelTestPoolReplenishmentExecutor") Executor executor,
            @Value("${language-learning.level-test.question-pool.replenish-batch-size:20}")
            int batchSize,
            @Value("${language-learning.level-test.question-pool.repair-batch-size:5}")
            int repairBatchSize
    ) {
        this.userSettingRepository = userSettingRepository;
        this.poolQueryService = poolQueryService;
        this.poolCommandService = poolCommandService;
        this.healthService = healthService;
        this.targetPlanner = targetPlanner;
        this.poolPolicy = poolPolicy;
        this.questionService = questionService;
        this.audioUploadService = audioUploadService;
        this.executor = executor;
        this.batchSize = Math.max(1, Math.min(100, batchSize));
        this.repairBatchSize = Math.max(0, Math.min(this.batchSize, repairBatchSize));
    }

    public ReplenishmentResult replenish() {
        LevelTestQuestionPoolHealthService.SweepResult sweep = healthService.sweep();
        int targetSize = poolPolicy.targetSize();

        int repairLimit = Math.min(repairBatchSize, batchSize);
        List<LevelTestQuestionPool> repairTargets = repairLimit <= 0
                ? List.of()
                : poolQueryService.pendingRepairs(repairLimit);
        ExecutionResult repairResult = executeRepairs(repairTargets);

        int generationLimit = Math.max(0, batchSize - repairTargets.size());
        List<GenerationJob> jobs = plan(targetSize, generationLimit);
        ExecutionResult generationResult = executeGenerationJobs(jobs);

        return new ReplenishmentResult(
                targetSize,
                repairTargets.size() + jobs.size(),
                repairResult.succeeded() + generationResult.succeeded(),
                repairResult.failed() + generationResult.failed(),
                sweep.scanned(),
                sweep.quarantined(),
                repairTargets.size(),
                repairResult.succeeded(),
                jobs.size(),
                generationResult.succeeded()
        );
    }

    List<GenerationJob> plan(int targetSize, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        PriorityQueue<BucketState> queue = new PriorityQueue<>(
                Comparator.comparingDouble(BucketState::fillRatio)
                        .thenComparing(
                                Comparator.comparingLong(BucketState::deficit)
                                        .reversed()
                        )
                        .thenComparing(value -> value.pair().key())
                        .thenComparing(value -> value.target().domain().name())
                        .thenComparing(value -> value.target().itemType().name())
                        .thenComparingInt(
                                value -> value.target().complexityBand()
                        )
        );

        for (LanguagePair pair : activeLanguagePairs()) {
            for (LevelTestQuestionPoolTargetPlanner.BucketTarget target
                    : targetPlanner.plan(targetSize)) {
                if (audioUploadService.requiresReferenceAudio(
                        target.domain(),
                        target.itemType()
                ) && !audioUploadService.available()) {
                    continue;
                }
                long current = poolQueryService.bucketCount(
                        pair.originLanguage(),
                        pair.learningLanguage(),
                        target.domain(),
                        target.itemType(),
                        target.complexityBand(),
                        LevelTestSession.DEFAULT_GENERATION_POLICY_VERSION,
                        LevelTestQuestionService.MODEL_CONFIG_VERSION
                );
                if (current < target.targetCount()) {
                    queue.add(new BucketState(pair, target, current));
                }
            }
        }

        List<GenerationJob> jobs = new ArrayList<>();
        while (!queue.isEmpty() && jobs.size() < limit) {
            BucketState state = queue.poll();
            jobs.add(new GenerationJob(
                    state.pair(),
                    state.target().representativeQuestionNumber(),
                    state.target().domain(),
                    state.target().itemType(),
                    state.target().complexityBand()
            ));
            BucketState next = state.incremented();
            if (next.deficit() > 0) {
                queue.add(next);
            }
        }
        return jobs;
    }

    private ExecutionResult executeRepairs(List<LevelTestQuestionPool> targets) {
        if (targets.isEmpty()) {
            return ExecutionResult.empty();
        }
        List<CompletableFuture<Boolean>> futures = targets.stream()
                .map(target -> CompletableFuture.supplyAsync(
                        () -> repair(target),
                        executor
                ))
                .toList();
        return await(futures);
    }

    private ExecutionResult executeGenerationJobs(List<GenerationJob> jobs) {
        if (jobs.isEmpty()) {
            return ExecutionResult.empty();
        }
        List<CompletableFuture<Boolean>> futures = jobs.stream()
                .map(job -> CompletableFuture.supplyAsync(
                        () -> generate(job),
                        executor
                ))
                .toList();
        return await(futures);
    }

    private ExecutionResult await(List<CompletableFuture<Boolean>> futures) {
        CompletableFuture.allOf(
                futures.toArray(CompletableFuture[]::new)
        ).join();
        int success = 0;
        for (CompletableFuture<Boolean> future : futures) {
            try {
                if (Boolean.TRUE.equals(future.join())) {
                    success++;
                }
            } catch (RuntimeException ignored) {
                // Individual failures are already logged at the job boundary.
            }
        }
        return new ExecutionResult(success, futures.size() - success);
    }

    private boolean repair(LevelTestQuestionPool target) {
        String batchKey = "repair-"
                + target.getId()
                + "-"
                + UUID.randomUUID();
        try {
            LevelTestQuestionPool replacement = questionService.generateBatchPoolQuestion(
                    target.getOriginLanguage(),
                    target.getLearningLanguage(),
                    target.getGeneratedForQuestionNumber(),
                    target.getComplexityBand(),
                    batchKey,
                    new DiversityContext(
                            List.of(),
                            List.of(),
                            List.of(),
                            poolQueryService.recentContentHashes(
                                    target.getOriginLanguage(),
                                    target.getLearningLanguage()
                            )
                    )
            );
            if (replacement == null || !replacement.isActive()) {
                return false;
            }
            poolCommandService.markReplaced(target.getId(), replacement.getId());
            log.info(
                    "Level Test quarantined pool question replaced. oldPoolQuestionId={}, newPoolQuestionId={}, reason={}",
                    target.getId(),
                    replacement.getId(),
                    target.getQuarantineReason()
            );
            return true;
        } catch (LevelTestPoolGenerationRejectedException rejection) {
            log.warn(
                    "Level Test quarantined pool question repair rejected by AI quality guard. "
                            + "poolQuestionId={}, quarantineReason={}, aiCode={}, reasons={}",
                    target.getId(),
                    target.getQuarantineReason(),
                    rejection.getCode(),
                    rejection.getReasons()
            );
            return false;
        } catch (RuntimeException exception) {
            log.warn(
                    "Level Test quarantined pool question repair failed. poolQuestionId={}, reason={}",
                    target.getId(),
                    target.getQuarantineReason(),
                    exception
            );
            return false;
        }
    }

    private boolean generate(GenerationJob job) {
        String batchKey = job.pair().originLanguage()
                + "-"
                + job.pair().learningLanguage()
                + "-"
                + job.domain()
                + "-"
                + job.itemType()
                + "-b"
                + job.complexityBand()
                + "-"
                + UUID.randomUUID();
        try {
            DiversityContext diversityContext = new DiversityContext(
                    List.of(),
                    List.of(),
                    List.of(),
                    poolQueryService.recentContentHashes(
                            job.pair().originLanguage(),
                            job.pair().learningLanguage()
                    )
            );
            var poolQuestion = questionService.generateBatchPoolQuestion(
                    job.pair().originLanguage(),
                    job.pair().learningLanguage(),
                    job.questionNumber(),
                    job.complexityBand(),
                    batchKey,
                    diversityContext
            );
            if (poolQuestion == null) {
                return false;
            }
            log.info(
                    "Level Test pool replenished. origin={}, learning={}, domain={}, itemType={}, band={}, poolQuestionId={}",
                    job.pair().originLanguage(),
                    job.pair().learningLanguage(),
                    job.domain(),
                    job.itemType(),
                    job.complexityBand(),
                    poolQuestion.getId()
            );
            return true;
        } catch (LevelTestPoolGenerationRejectedException rejection) {
            log.warn(
                    "Level Test pool generation rejected by AI quality guard. "
                            + "origin={}, learning={}, domain={}, itemType={}, band={}, aiCode={}, reasons={}",
                    job.pair().originLanguage(),
                    job.pair().learningLanguage(),
                    job.domain(),
                    job.itemType(),
                    job.complexityBand(),
                    rejection.getCode(),
                    rejection.getReasons()
            );
            return false;
        } catch (RuntimeException exception) {
            log.warn(
                    "Level Test pool replenishment failed. origin={}, learning={}, domain={}, itemType={}, band={}",
                    job.pair().originLanguage(),
                    job.pair().learningLanguage(),
                    job.domain(),
                    job.itemType(),
                    job.complexityBand(),
                    exception
            );
            return false;
        }
    }

    private List<LanguagePair> activeLanguagePairs() {
        Set<LanguagePair> result = new HashSet<>();
        for (LanguageLearningUserSetting setting : userSettingRepository
                .findAllByOriginLanguageIsNotNullAndLearningLanguageIsNotNull()) {
            if (setting.getOriginLanguage() == null
                    || setting.getOriginLanguage().isBlank()
                    || setting.getLearningLanguage() == null
                    || setting.getLearningLanguage().isBlank()) {
                continue;
            }
            result.add(new LanguagePair(
                    setting.getOriginLanguage(),
                    setting.getLearningLanguage()
            ));
        }
        return result.stream()
                .sorted(Comparator.comparing(LanguagePair::key))
                .toList();
    }

    public record ReplenishmentResult(
            int targetSize,
            int requested,
            int succeeded,
            int failed,
            int healthScanned,
            int quarantined,
            int repairRequested,
            int repaired,
            int generationRequested,
            int generated
    ) {
    }

    record GenerationJob(
            LanguagePair pair,
            int questionNumber,
            jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain domain,
            jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemType itemType,
            int complexityBand
    ) {
    }

    record LanguagePair(String originLanguage, String learningLanguage) {
        String key() {
            return originLanguage + "->" + learningLanguage;
        }
    }

    private record ExecutionResult(int succeeded, int failed) {
        static ExecutionResult empty() {
            return new ExecutionResult(0, 0);
        }
    }

    private record BucketState(
            LanguagePair pair,
            LevelTestQuestionPoolTargetPlanner.BucketTarget target,
            long current
    ) {
        long deficit() {
            return Math.max(0L, target.targetCount() - current);
        }

        double fillRatio() {
            return target.targetCount() <= 0
                    ? 1.0
                    : (double) current / target.targetCount();
        }

        BucketState incremented() {
            return new BucketState(pair, target, current + 1);
        }
    }
}
