package jp.co.translacat.domain.documenttranslation;

import jp.co.translacat.domain.documenttranslation.format.DocumentFormatPort;
import jp.co.translacat.domain.documenttranslation.translation.DocumentTranslationGateway;
import jp.co.translacat.domain.documenttranslation.translation.DocumentTranslationGateway.Batch;
import jp.co.translacat.domain.documenttranslation.translation.DocumentTranslationGateway.Failure;
import jp.co.translacat.domain.documenttranslation.translation.DocumentTranslationGateway.Reason;
import jp.co.translacat.domain.documenttranslation.translation.DocumentTranslationGateway.RepairPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/** 문서 접수와 번역을 실행 풀, 리스, 전체 마감으로 격리한다. */
final class DocumentTranslationWorker implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentTranslationWorker.class);
    private static final int WORKERS = 2;
    private static final int EFFECTIVE_BATCH_CONCURRENCY = 2;
    private static final int UNITS_PER_BATCH = 32;
    private static final int CHARACTERS_PER_BATCH = 4_000;

    private final DocumentJobRepository repository;
    private final DocumentTransientFiles files;
    private final DocumentFormatPort formats;
    private final DocumentTranslationGateway translator;
    private final Clock clock;
    private final ExecutorService executor = Executors.newFixedThreadPool(WORKERS);
    private final ExecutorService batchExecutor = Executors.newFixedThreadPool(WORKERS * EFFECTIVE_BATCH_CONCURRENCY);
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService maintenance = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cleaning = new AtomicBoolean();
    private final Semaphore slots = new Semaphore(WORKERS);

    // 각 포트를 모듈의 작업 처리 흐름으로 연결한다.
    DocumentTranslationWorker(DocumentJobRepository repository, DocumentTransientFiles files,
            DocumentFormatPort formats, DocumentTranslationGateway translator, Clock clock) {
        this.repository = repository;
        this.files = files;
        this.formats = formats;
        this.translator = translator;
        this.clock = clock;
    }

    /** 공유 스케줄러에서 업무 처리를 기다리지 않고 비어 있는 실행 슬롯에 넘긴다. */
    @Scheduled(fixedDelay = 1000)
    public void dispatch() {
        repository.sweep(clock.instant());
        while (slots.tryAcquire()) {
            boolean submitted = false;
            try {
                var claimed = repository.claim(clock.instant());
                if (claimed.isEmpty()) {
                    return;
                }
                executor.submit(() -> execute(claimed.get()));
                submitted = true;
            } finally {
                if (!submitted) {
                    slots.release();
                }
            }
        }
    }

    /** DB에 연결되지 못한 작업까지 로컬 만료 기한에 따라 주기적으로 회수한다. */
    @Scheduled(fixedDelay = 5_000, initialDelay = 5_000)
    public void cleanExpired() {
        // 로컬 파일 삭제가 다른 업무의 공용 스케줄을 막지 않도록 한다.
        if (cleaning.compareAndSet(false, true)) {
            maintenance.submit(() -> {
                try {
                    files.reap(clock.instant());
                } catch (IOException unavailable) {
                    LOGGER.warn("Document transient cleanup deferred");
                } finally {
                    cleaning.set(false);
                }
            });
        }
    }

    /** 파일 수명과 별개로 기한이 끝난 문서 상태 메타데이터를 회수한다. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void cleanMetadata() {
        repository.removeOldMetadata(clock.instant());
    }

    // 현재 시도에서 사용한 임대와 로컬 사본을 종료 상태와 함께 정리한다.
    private void execute(DocumentJob job) {
        var pulse = heartbeat.scheduleAtFixedRate(() -> renew(job), 10, 10, TimeUnit.SECONDS);
        boolean published = false;
        try {
            translate(job);
            published = true;
        } catch (JobFailure failure) {
            repository.fail(job, failure.code, clock.instant());
            LOGGER.info("Document job failed jobId={} code={}", job.id(), failure.code);
        } catch (LostLease superseded) {
            // 다른 시도가 인계했거나 취소된 작업에는 더 이상 쓰지 않는다.
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException failed) {
            repository.fail(job, "WORKER_UNAVAILABLE", clock.instant());
            LOGGER.warn("Document worker stopped jobId={} kind={}", job.id(), failed.getClass().getSimpleName());
        } finally {
            pulse.cancel(false);
            try {
                // 발행하지 못한 작업의 파일은 DB 정리 결과와 무관하게 회수한다.
                if (!published) {
                    discard(job);
                }
                repository.completeCancel(job, clock.instant());
            } finally {
                slots.release();
            }
        }
    }

    // 임대 저장이 잠시 실패해도 다음 갱신 기회를 두며 저장소가 최종 쓰기를 검증한다.
    private void renew(DocumentJob job) {
        try {
            repository.renew(job, clock.instant());
        } catch (RuntimeException unavailable) {
            LOGGER.warn("Document lease heartbeat failed jobId={}", job.id());
        }
    }

    // 검사·번역·재구성은 작업 DB의 클레임 트랜잭션 밖에서 처리한다.
    private void translate(DocumentJob job) throws IOException, InterruptedException {
        if (!repository.canRun(job, DocumentJob.State.VALIDATING, clock.instant())) {
            throw new LostLease();
        }
        long bytes;
        List<DocumentJob.Warning> outputWarnings;
        try (var lease = files.use(job.id())) {
            // 업로드 때 만들어진 같은 작업의 원본만 읽고 검사한다.
            Path source = files.source(job.id());
            if (Files.size(source) != job.inputBytes()) {
                throw new JobFailure("WORKER_UNAVAILABLE");
            }
            DocumentFormatPort.Plan plan = read(job, source);
            List<List<DocumentFormatPort.Segment>> batches = split(plan.segments());
            int total = plan.segments().size();
            String model = job.modelRevision();
            advance(job, DocumentJob.State.TRANSLATING, total, 0, model);

            // 번역 조각의 검증과 선택 재시도는 현재 작업의 메모리에서만 수행한다.
            var completed = processBatches(job, batches, total, model);

            // 재패키징과 무결성 확인을 마치면 응답용 결과를 공개하기 전에 원본을 지운다.
            advance(job, DocumentJob.State.REPACKING, total, total, completed.model());
            Path output = files.result(job.id());
            try {
                outputWarnings = warnings(plan.warnings(), formats.write(plan, completed.translatedPieces(), output));
            } catch (IOException invalid) {
                throw new JobFailure("OUTPUT_INTEGRITY_FAILED");
            }
            bytes = Files.size(output);
            files.deleteSource(job.id());
        }

        // 성공 응답을 조회하는 순간에는 더 이상 원본 또는 결과를 쓰는 처리 사용권이 없다.
        publish(job, bytes, outputWarnings);
    }

    // 검사기의 실패 범위를 작업 오류로 변환하고 대상이 빈 파일도 안내한다.
    private DocumentFormatPort.Plan read(DocumentJob job, Path source) {
        try {
            DocumentFormatPort.Plan plan = formats.read(source, job.format());
            if (plan.segments().isEmpty()) {
                throw new JobFailure("NO_TRANSLATABLE_TEXT");
            }
            return plan;
        } catch (DocumentFormatPort.Rejected rejected) {
            throw new JobFailure(rejected.reason().name());
        } catch (IOException malformed) {
            throw new JobFailure("INVALID_PACKAGE");
        }
    }

    // 문맥을 보존하는 세그먼트를 최대 개수 또는 입력 문자 수에서 나눈다.
    private static List<List<DocumentFormatPort.Segment>> split(List<DocumentFormatPort.Segment> segments) {
        if (segments.size() > 2_000) {
            throw new JobFailure(DocumentFormatPort.Rejection.DOCUMENT_SEGMENT_LIMIT.name());
        }
        var batches = new ArrayList<List<DocumentFormatPort.Segment>>();
        var current = new ArrayList<DocumentFormatPort.Segment>();
        int documentsCharacters = 0;
        int batchCharacters = 0;
        for (var segment : segments) {
            int length = segment.pieces().stream()
                    .mapToInt(piece -> piece.text().codePointCount(0, piece.text().length())).sum();
            documentsCharacters += length;
            if (length > 2_000) {
                throw new JobFailure(DocumentFormatPort.Rejection.DOCUMENT_UNIT_TEXT_LIMIT.name());
            }
            if (documentsCharacters > 30_000) {
                throw new JobFailure(DocumentFormatPort.Rejection.DOCUMENT_TEXT_LIMIT.name());
            }
            if (!current.isEmpty()
                    && (current.size() == UNITS_PER_BATCH || batchCharacters + length > CHARACTERS_PER_BATCH)) {
                batches.add(List.copyOf(current));
                current.clear();
                batchCharacters = 0;
            }
            current.add(segment);
            batchCharacters += length;
        }
        if (!current.isEmpty()) {
            batches.add(List.copyOf(current));
        }
        return batches;
    }

    // 문서 내부의 두 배치까지 병행하며 결과와 진행 상태는 원래 순서로 합친다.
    private Batch processBatches(DocumentJob job, List<List<DocumentFormatPort.Segment>> batches, int total,
            String revision) throws IOException, InterruptedException {
        var inFlight = new ArrayDeque<Future<Batch>>();
        var translated = new LinkedHashMap<String, String>();
        String model = revision;
        int completed = 0;
        int next = 0;
        try {
            while (next < Math.min(batches.size(), EFFECTIVE_BATCH_CONCURRENCY)) {
                inFlight.addLast(submitBatch(job, next, batches.get(next)));
                next++;
            }

            for (int index = 0; index < batches.size(); index++) {
                Batch result = await(inFlight.getFirst());
                inFlight.removeFirst();
                if (model != null && !model.equals(result.model())) {
                    throw new JobFailure("TRANSLATION_UNAVAILABLE");
                }
                model = result.model();
                translated.putAll(result.translatedPieces());
                completed += batches.get(index).size();
                advance(job, DocumentJob.State.TRANSLATING, total, completed, model);
                if (next < batches.size()) {
                    inFlight.addLast(submitBatch(job, next, batches.get(next)));
                    next++;
                }
            }
        } finally {
            inFlight.forEach(future -> future.cancel(true));
        }
        return new Batch(translated, model);
    }

    // 외부 번역 호출은 작업 스레드와 별도의 고정 슬롯을 사용한다.
    private Future<Batch> submitBatch(DocumentJob job, int index, List<DocumentFormatPort.Segment> segments) {
        return batchExecutor.submit(() -> batch(job, index, segments));
    }

    // 병렬 실행에서 작업 실패와 인터럽트를 호출자에게 그대로 전달한다.
    private static Batch await(Future<Batch> pending) throws IOException, InterruptedException {
        try {
            return pending.get();
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof IOException unavailable) {
                throw unavailable;
            }
            if (cause instanceof InterruptedException interrupted) {
                throw interrupted;
            }
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IOException("Document translation batch did not complete");
        }
    }

    // 현재 리스를 확인한 후 이 배치의 원문과 결과를 메모리 내 처리로 제한한다.
    private Batch batch(DocumentJob job, int number, List<DocumentFormatPort.Segment> units)
            throws InterruptedException {
        checkRunning(job);
        return call(job, number, units);
    }

    // 기존 한 번의 재시도 안에서 유효한 응답의 일부가 실패하면 그 세그먼트만 다시 요청한다.
    private Batch call(DocumentJob job, int number, List<DocumentFormatPort.Segment> units)
            throws InterruptedException {
        Instant deadline = job.createdAt().plusSeconds(DocumentTranslationService.JOB_SECONDS);
        String code = "TRANSLATION_UNAVAILABLE";
        RepairPlan repair = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            if (!clock.instant().isBefore(deadline)) {
                throw new JobFailure("TRANSLATION_TIMEOUT");
            }
            checkRunning(job);
            try {
                List<DocumentFormatPort.Segment> requested = repair == null ? units : repair.rejected();
                Batch translated = translator.translate(job.id(), number, job.sourceLanguage(), job.targetLanguage(),
                        requested, deadline);
                if (repair != null) {
                    translated = mergeRepair(units, repair, translated);
                }
                return translated;
            } catch (Failure unavailable) {
                code = unavailable.reason() == Reason.TIMEOUT
                        ? "TRANSLATION_TIMEOUT" : "TRANSLATION_UNAVAILABLE";
                Duration delay = unavailable.retryAfter() == null ? Duration.ofMillis(300) : unavailable.retryAfter();
                boolean retry = attempt == 0 && unavailable.retryable() && clock.instant().plus(delay).isBefore(deadline);
                if (!retry) {
                    throw new JobFailure(code);
                }
                repair = unavailable.repairPlan();
                Thread.sleep(delay);
            } catch (IOException unavailable) {
                if (attempt == 0) {
                    Thread.sleep(300);
                }
            }
        }
        throw new JobFailure(clock.instant().isBefore(deadline) ? code : "TRANSLATION_TIMEOUT");
    }

    // 같은 모델과 빠짐없는 조각을 확인해 일부만 번역된 배치가 결과로 가지 않게 한다.
    private static Batch mergeRepair(List<DocumentFormatPort.Segment> original, RepairPlan plan, Batch repaired)
            throws Failure {
        if (!plan.accepted().model().equals(repaired.model())) {
            throw new Failure(Reason.CONFIGURATION, Duration.ZERO);
        }
        var expected = plan.rejected().stream()
                .flatMap(segment -> segment.pieces().stream())
                .map(DocumentFormatPort.Piece::id)
                .collect(Collectors.toSet());
        if (!expected.equals(repaired.translatedPieces().keySet())) {
            throw new Failure(Reason.INVALID_RESPONSE, Duration.ZERO);
        }
        var complete = new LinkedHashMap<>(plan.accepted().translatedPieces());
        for (var entry : repaired.translatedPieces().entrySet()) {
            if (complete.putIfAbsent(entry.getKey(), entry.getValue()) != null) {
                throw new Failure(Reason.INVALID_RESPONSE, Duration.ZERO);
            }
        }
        var originalIds = original.stream()
                .flatMap(segment -> segment.pieces().stream())
                .map(DocumentFormatPort.Piece::id)
                .collect(Collectors.toSet());
        if (!originalIds.equals(complete.keySet())) {
            throw new Failure(Reason.INVALID_RESPONSE, Duration.ZERO);
        }
        return new Batch(complete, repaired.model());
    }

    // 취소 또는 소유자 교체가 끝난 뒤 새 AI 요청을 시작하지 않는다.
    private void checkRunning(DocumentJob job) {
        if (!repository.canTranslate(job, clock.instant())) {
            throw new LostLease();
        }
    }

    // 현재 상태를 교체할 수 없으면 남은 작업을 계속하지 않는다.
    private void advance(DocumentJob job, DocumentJob.State stage, int total, int completed, String model) {
        Instant now = clock.instant();
        if (!now.isBefore(job.createdAt().plusSeconds(DocumentTranslationService.JOB_SECONDS))) {
            throw new JobFailure("TRANSLATION_TIMEOUT");
        }
        if (!repository.progress(job, stage, total, completed, model, now)) {
            throw new LostLease();
        }
    }

    // 원본이 사라진 뒤 일회 수령 기한을 설정하고 현재 리스로 결과를 공개한다.
    private void publish(DocumentJob job, long bytes, List<DocumentJob.Warning> warnings) throws IOException {
        if (bytes <= 0) {
            throw new JobFailure("OUTPUT_INTEGRITY_FAILED");
        }

        Instant now = clock.instant();
        if (!now.isBefore(job.createdAt().plusSeconds(DocumentTranslationService.JOB_SECONDS))) {
            throw new JobFailure("TRANSLATION_TIMEOUT");
        }
        files.retainUntil(job.id(), now.plusSeconds(DocumentTranslationService.RESULT_SECONDS));
        if (!repository.succeed(job, DocumentTranslationService.resultMarker(job.id()), bytes, warnings, now)) {
            throw new LostLease();
        }
    }

    // 파일 제거 실패 때도 다른 작업은 계속 실행하고 로컬 회수기가 다시 확인한다.
    private void discard(DocumentJob job) {
        try {
            files.discard(job.id());
        } catch (IOException unavailable) {
            LOGGER.warn("Document transient cleanup deferred jobId={}", job.id());
        }
    }

    // 문서 내부 위치는 지속 저장하지 않고 사용자에게 경고 종류와 발생 횟수만 전달한다.
    private static List<DocumentJob.Warning> warnings(List<DocumentFormatPort.Warning> extraction,
            List<DocumentFormatPort.Warning> writing) {
        Map<String, List<DocumentFormatPort.Warning>> byCode = java.util.stream.Stream.concat(
                extraction.stream(), writing.stream()).distinct().collect(Collectors.groupingBy(
                        DocumentFormatPort.Warning::code, LinkedHashMap::new, Collectors.toList()));
        return byCode.entrySet().stream().map(entry -> new DocumentJob.Warning(entry.getKey(),
                entry.getValue().size(), List.of())).toList();
    }

    /** 애플리케이션 종료 시 워커와 리스 스레드에 정지 신호를 보낸다. */
    @Override
    public void close() {
        executor.shutdownNow();
        batchExecutor.shutdownNow();
        heartbeat.shutdownNow();
        maintenance.shutdownNow();
    }

    private static final class LostLease extends RuntimeException {}

    private static final class JobFailure extends RuntimeException {
        private final String code;

        // 실제 파일이나 공급자 메시지가 포함되지 않는 작업 코드를 저장한다.
        private JobFailure(String code) {
            this.code = code;
        }
    }
}
