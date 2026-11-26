package jp.co.translacat.domain.imagetranslation;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import static jp.co.translacat.domain.imagetranslation.ImageTranslationJobs.JOB_RESULT_TTL_MS;
import static jp.co.translacat.domain.imagetranslation.ImageTranslationJobs.now;
import static jp.co.translacat.domain.imagetranslation.ImageTranslationTypes.*;

@Component
@ConditionalOnProperty(prefix = "translacat.image-translation", name = "enabled", havingValue = "true")
class ImageTranslationWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageTranslationWorker.class);
    private static final int ATTEMPT_LIMIT = 3;
    private static final long LEASE_MS = Duration.ofMinutes(2).toMillis();
    private static final long CLEANUP_GRACE_MS = Duration.ofMinutes(3).toMillis();
    private static final long CLEANUP_RETRY_MS = Duration.ofMinutes(1).toMillis();

    private final ImageTranslationStore store;
    private final ImageTranslationInput input;
    private final ImageTranslationEngine engine;
    private final ImageTranslationAssets assets;
    private final ExecutorService workers;
    private final Semaphore capacity;
    private final AtomicBoolean cleanupRunning = new AtomicBoolean();

    // 웹툰의 병렬 작업 상한을 다른 기능 작업자와 분리한다.
    ImageTranslationWorker(ImageTranslationStore store, ImageTranslationInput input, ImageTranslationEngine engine,
            ImageTranslationAssets assets, @Value("${translacat.image-translation.workers:4}") int concurrency) {
        if (concurrency < 1 || concurrency > 8) {
            throw new IllegalArgumentException("Image translation worker count must be between 1 and 8");
        }
        this.store = store;
        this.input = input;
        this.engine = engine;
        this.assets = assets;
        capacity = new Semaphore(concurrency);
        var threadFactory = Thread.ofPlatform().name("image-translation-", 0).factory();
        workers = Executors.newFixedThreadPool(concurrency, threadFactory);
    }

    // 여유 슬롯만큼 오래 대기한 회차를 순회해 회차별 첫 순서부터 접수한다.
    @Scheduled(fixedDelayString = "${translacat.image-translation.poll-ms:250}")
    void dispatch() {
        int slots = capacity.availablePermits();
        if (slots == 0) {
            return;
        }
        List<UUID> candidates = store.workIds(now());

        // 회차를 한 번씩 순회한 뒤 남은 실행 슬롯에 추가 페이지를 배정한다.
        for (int round = 0; round < slots && capacity.availablePermits() > 0; round++) {
            boolean scheduled = false;
            for (UUID id : candidates) {
                if (!capacity.tryAcquire()) {
                    return;
                }
                Runnable task;
                try {
                    task = store.transaction(() -> claim(id));
                } catch (RuntimeException error) {
                    capacity.release();
                    throw error;
                }
                if (task == null) {
                    capacity.release();
                    continue;
                }
                queueReserved(task);
                scheduled = true;
            }
            if (!scheduled) {
                return;
            }
        }
    }

    // 자산 정리도 웹툰 전용 작업자에서 수행해 공유 스케줄러에서 외부 I/O를 기다리지 않는다.
    @Scheduled(fixedDelayString = "${translacat.image-translation.cleanup-poll-ms:30000}")
    void dispatchCleanup() {
        if (capacity.availablePermits() == 0 || !cleanupRunning.compareAndSet(false, true)) {
            return;
        }
        if (!queue(() -> {
            try {
                cleanup();
            } finally {
                cleanupRunning.set(false);
            }
        })) {
            cleanupRunning.set(false);
        }
    }

    // 종료 시 이 모듈의 스레드만 정리한다. 완료되지 않은 임대는 재기동 후 만료 기준으로 복구한다.
    @PreDestroy
    void shutdown() {
        workers.shutdownNow();
    }

    // 예약된 한 작업을 수행하고 예외가 발생해도 다음 작업의 슬롯을 반환한다.
    private boolean queue(Runnable task) {
        if (!capacity.tryAcquire()) {
            return false;
        }
        queueReserved(task);
        return true;
    }

    // 예약한 슬롯이 닫힌 실행기에서 거절되더라도 자원 수를 보존한다.
    private void queueReserved(Runnable task) {
        try {
            workers.execute(() -> execute(task));
        } catch (java.util.concurrent.RejectedExecutionException error) {
            capacity.release();
            throw error;
        }
    }

    // 실행 실패가 후속 작업을 막지 않도록 슬롯과 안전한 상태 로그를 관리한다.
    private void execute(Runnable task) {
        try {
            task.run();
        } catch (RuntimeException error) {
            LOGGER.warn("Image translation worker failed: {}", error.getClass().getSimpleName());
        } finally {
            capacity.release();
        }
    }

    // 짧은 DB 잠금에서 회차 또는 페이지 실행권만 부여한다.
    private Runnable claim(UUID id) {
        Job job = store.lockWritableJob(id);
        long time = now();
        if (job == null || job.expiresAt <= time) {
            return null;
        }
        if (job.status == JobStatus.PREPARING) {
            return claimPreparation(job, time);
        }
        if (job.status != JobStatus.QUEUED && job.status != JobStatus.RUNNING) {
            return null;
        }

        // 이전 작업자가 재기동 등으로 반환하지 못한 페이지도 임대가 끝나면 회수한다.
        boolean changed = false;
        for (Page page : job.pages) {
            if (!claimable(page, time)) {
                continue;
            }
            if (page.attempt >= ATTEMPT_LIMIT) {
                page.state = PageState.FAILED;
                page.lease = null;
                page.failure = new Failure("WORKER_TIMEOUT", Stage.OCR, true);
                changed = true;
                continue;
            }
            page.attempt++;
            page.lease = UUID.randomUUID();
            page.leaseUntil = time + LEASE_MS;
            page.state = PageState.RUNNING;
            job.status = JobStatus.RUNNING;
            touch(job, time);
            var claim = new PageClaim(job.id, page.id, page.lease, job.sourceLanguage, job.targetLanguage, page);
            return () -> runPage(claim);
        }
        if (changed) {
            settle(job, time);
            touch(job, time);
        }
        return null;
    }

    // 아직 유효한 다른 준비 실행권은 유지하고, 무한 재기동은 시도 횟수로 차단한다.
    private Runnable claimPreparation(Job job, long time) {
        if (job.prepareLeaseUntil > time) {
            return null;
        }
        if (job.prepareAttempts >= ATTEMPT_LIMIT) {
            job.status = JobStatus.FAILED;
            job.failure = new Failure("PREPARATION_TIMEOUT", Stage.ACQUIRE, false);
            job.expiresAt = time + JOB_RESULT_TTL_MS;
            touch(job, time);
            return null;
        }
        job.prepareAttempts++;
        job.prepareLease = UUID.randomUUID();
        job.prepareLeaseUntil = time + LEASE_MS;
        touch(job, time);
        var claim = new PrepareClaim(job, job.prepareLease);
        return () -> prepare(claim);
    }

    // 해제·PNG 검사·커넥터 호출은 DB 트랜잭션 밖에서 수행한다.
    private void prepare(PrepareClaim claim) {
        Prepared prepared = null;
        Failure failure = null;
        try {
            prepared = input.prepare(claim.job());
            if (prepared.pages().isEmpty()) {
                failure = new Failure("INVALID_IMAGE", Stage.VALIDATE, false);
            }
        } catch (WorkFailure error) {
            failure = error.failure;
        } catch (IOException | RuntimeException error) {
            logFailure("prepare", error);
            failure = new Failure(claim.job().sourceKind == SourceKind.LINK
                    ? "LINK_PREPARATION_INVALID" : "ASSET_STORE_TEMPORARY", Stage.ACQUIRE, true);
        }
        finishPreparation(claim, prepared, failure);
    }

    // 유효한 준비 작업자의 결과만 공개한다. 임대가 끝나거나 취소된 작업은 버린다.
    private void finishPreparation(PrepareClaim claim, Prepared prepared, Failure failure) {
        store.transaction(() -> {
            Job job = store.lockWritableJob(claim.job().id);
            long time = now();
            if (job == null || job.status != JobStatus.PREPARING || job.expiresAt <= time
                    || !claim.token().equals(job.prepareLease) || job.prepareLeaseUntil <= time) {
                return null;
            }
            job.prepareLease = null;
            job.prepareLeaseUntil = 0;

            // 일시적인 준비 오류는 같은 후보 원본을 최대 세 번 확인한다.
            if (failure != null && failure.retryable() && job.prepareAttempts < ATTEMPT_LIMIT) {
                job.prepareLeaseUntil = time + retryDelay(job.prepareAttempts);
                touch(job, time);
                return null;
            }
            if (prepared != null) {
                job.pages = prepared.pages();
                job.inputIssues = prepared.issues();
                job.attribution = prepared.attribution();
            }
            job.status = failure == null ? JobStatus.READY : JobStatus.FAILED;
            if (failure != null) {
                job.failure = new Failure(failure.code(), failure.stage(), false);
                job.expiresAt = time + JOB_RESULT_TTL_MS;
            }
            touch(job, time);
            return null;
        });
    }

    // 링크 원본을 준비한 뒤 OCR·번역을 호출한다. I/O가 끝날 때까지 행 잠금을 보유하지 않는다.
    private void runPage(PageClaim claim) {
        Page page = claim.page();
        EngineResult result = null;
        Failure failure = null;
        Stage stage = page.sourcePath == null ? Stage.STORE : Stage.ACQUIRE;
        try {
            if (page.image == null) {
                input.acquire(claim.jobId(), page);
            }
            stage = Stage.STORE;
            byte[] bytes = input.imageBytes(claim.jobId(), page.objectId);
            if (!ImageTranslationAssets.hash(bytes).equals(page.sha256)) {
                throw new WorkFailure("ORIGINAL_MISMATCH", Stage.STORE, false);
            }
            stage = Stage.OCR;
            result = engine.process(bytes, page.image, claim.sourceLanguage(), claim.targetLanguage());
            if (result.state() == PageState.FAILED) {
                Stage reason = "TRANSLATION_TEMPORARY".equals(result.errorCode()) ? Stage.TRANSLATE : Stage.OCR;
                failure = new Failure(result.errorCode(), reason, result.retryable());
            }
        } catch (WorkFailure error) {
            failure = error.failure;
        } catch (IOException | RuntimeException error) {
            logFailure("page", error);
            String code = switch (stage) {
                case ACQUIRE -> "LINK_ASSET_UNAVAILABLE";
                case OCR -> "OCR_TEMPORARY";
                default -> "ASSET_STORE_TEMPORARY";
            };
            failure = new Failure(code, stage, true);
        }
        finishPage(claim, result, failure);
    }

    // 현재 실행권과 만료 시각을 검증한 뒤 페이지와 회차 집계를 한 트랜잭션에 반영한다.
    private void finishPage(PageClaim claim, EngineResult result, Failure failure) {
        store.transaction(() -> {
            Job job = store.lockWritableJob(claim.jobId());
            long time = now();
            if (job == null || job.status != JobStatus.RUNNING || job.expiresAt <= time) {
                return null;
            }
            Page page = job.pages.stream().filter(candidate -> candidate.id.equals(claim.pageId())).findFirst()
                    .orElse(null);
            if (page == null || page.state != PageState.RUNNING || !claim.token().equals(page.lease)
                    || page.leaseUntil <= time) {
                return null;
            }

            // 링크에서 뒤늦게 알게 된 픽셀도 전체 작업 상한을 적용한 뒤 공개한다.
            Failure finalFailure = failure;
            if (page.image == null && claim.page().image != null) {
                long pixels = job.pages.stream().filter(candidate -> candidate.image != null)
                        .mapToLong(candidate -> candidate.image.pixels()).sum();
                if (pixels + claim.page().image.pixels() > MAX_EPISODE_PIXELS) {
                    finalFailure = new Failure("INPUT_LIMIT_EXCEEDED", Stage.VALIDATE, false);
                } else if (claim.page().sha256 != null) {
                    page.image = claim.page().image;
                    page.sha256 = claim.page().sha256;
                }
            }

            // 확정된 일부 결과도 보존하되 완료 건수에는 포함하지 않는다.
            page.lease = null;
            page.leaseUntil = 0;
            if (result != null && page.image != null) {
                page.result = result;
            }
            page.failure = finalFailure;
            if (finalFailure == null) {
                page.state = result.state();
            } else if (finalFailure.retryable() && page.attempt < ATTEMPT_LIMIT) {
                page.state = PageState.PENDING;
                page.nextRun = time + retryDelay(page.attempt);
            } else {
                page.state = PageState.FAILED;
            }
            settle(job, time);
            touch(job, time);
            return null;
        });
    }

    // 처리할 페이지가 모두 끝난 뒤 성공·일부 실패·실패와 보존 시각을 확정한다.
    private static void settle(Job job, long time) {
        if (job.pages.stream().anyMatch(page -> page.state == PageState.PENDING || page.state == PageState.RUNNING)) {
            return;
        }
        long readable = job.pages.stream().filter(page -> page.state == PageState.READY
                || page.state == PageState.NO_TEXT).count();
        if (readable == job.pages.size()) {
            job.status = JobStatus.SUCCEEDED;
        } else {
            job.status = readable == 0 ? JobStatus.FAILED : JobStatus.PARTIAL_FAILED;
        }
        job.expiresAt = time + JOB_RESULT_TTL_MS;
    }

    // 만료를 먼저 고정한 뒤 객체 정리를 시도하고 성공 뒤 짧은 오류 응답 이력만 남긴다.
    private void cleanup() {
        long time = now();
        for (UUID id : store.expiredIds(time)) {
            store.transaction(() -> {
                Job job = store.lockWritableJob(id);
                if (job != null && job.status != JobStatus.EXPIRED && job.expiresAt <= time) {
                    job.status = JobStatus.EXPIRED;
                    job.pages = List.of();
                    job.inputs = List.of();
                    job.inputIssues = List.of();
                    job.sourceLink = null;
                    job.attribution = null;
                    job.cleanupDue = time + CLEANUP_GRACE_MS;
                    job.tombstoneUntil = time + JOB_RESULT_TTL_MS;
                    touch(job, time);
                }
                return null;
            });
        }

        // 정리 호출 중 실패해도 DB 이력을 지우지 않고 다음 정리 시도 시각을 기록한다.
        for (UUID id : store.cleanupIds(time)) {
            removeExpiredAssets(id, time);
        }
    }

    // 정리 권한을 예약하고, 저장소 실행 이후 소유자와 작업 이력을 다시 잠근다.
    private void removeExpiredAssets(UUID id, long time) {
        long lease = time + CLEANUP_GRACE_MS;
        boolean acquired = Boolean.TRUE.equals(store.transaction(() -> {
            Job job = store.lockWritableJob(id);
            if (job == null || job.status != JobStatus.EXPIRED || job.cleanupDue > time) {
                return false;
            }
            job.cleanupDue = lease;
            store.save(job, time);
            return true;
        }));
        if (!acquired) {
            return;
        }

        // I/O 중 계정 삭제가 시작됐다면 이 실행이 저장 상태를 다시 살리지 않는다.
        boolean removed = removeAssets(id);
        store.transaction(() -> {
            Job job = store.lockWritableJob(id);
            if (job == null || job.status != JobStatus.EXPIRED || job.cleanupDue != lease) {
                return null;
            }
            if (removed && job.tombstoneUntil <= time) {
                store.delete(id);
                return null;
            }
            job.cleanupDue = removed ? job.tombstoneUntil : now() + CLEANUP_RETRY_MS;
            store.save(job, time);
            return null;
        });
    }

    // 저장소 실패는 재시도할 수 있도록 정리 결과만 반환한다.
    private boolean removeAssets(UUID id) {
        try {
            assets.removeJob(id);
            return true;
        } catch (IOException | RuntimeException error) {
            logFailure("cleanup", error);
            return false;
        }
    }

    // 공개 상태마다 revision과 워커의 정렬 시각을 동시에 갱신한다.
    private void touch(Job job, long time) {
        job.revision++;
        store.save(job, time);
    }

    private static boolean claimable(Page page, long time) {
        return (page.state == PageState.PENDING && page.nextRun <= time)
                || (page.state == PageState.RUNNING && page.leaseUntil <= time);
    }

    private static long retryDelay(int attempt) {
        return 1000L << Math.max(0, attempt - 1);
    }

    // 요청 URL, 이미지 및 번역문은 예외에 포함될 수 있으므로 고정 단계와 예외 유형만 남긴다.
    private static void logFailure(String phase, Exception error) {
        LOGGER.warn("Image translation operation failed: phase={}, type={}", phase, error.getClass().getSimpleName());
    }
}
