package jp.co.translacat.domain.documenttranslation;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.documenttranslation.format.DocumentFormatPort;
import jp.co.translacat.domain.documenttranslation.translation.DocumentTranslationGateway;
import jp.co.translacat.domain.documenttranslation.translation.DocumentTranslationGateway.Batch;
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
import java.util.HexFormat;
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
    private final DocumentFiles files;
    private final DocumentFormatPort formats;
    private final DocumentTranslationGateway translator;
    private final ObjectMapper mapper;
    private final DocumentObjectWrites writes;
    private final Clock clock;
    private final ExecutorService executor = Executors.newFixedThreadPool(WORKERS);
    private final ExecutorService batchExecutor = Executors.newFixedThreadPool(WORKERS * EFFECTIVE_BATCH_CONCURRENCY);
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService maintenance = Executors.newSingleThreadExecutor();
    private final AtomicBoolean cleaning = new AtomicBoolean();
    private final Semaphore slots = new Semaphore(WORKERS);

    // 각 포트를 모듈의 작업 처리 흐름으로 연결한다.
    DocumentTranslationWorker(DocumentJobRepository repository, DocumentFiles files, DocumentFormatPort formats,
            DocumentTranslationGateway translator, ObjectMapper mapper, DocumentObjectWrites writes, Clock clock) {
        this.repository = repository;
        this.files = files;
        this.formats = formats;
        this.translator = translator;
        this.mapper = mapper;
        this.writes = writes;
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

    /** 미완료 요청이나 영속 작업과 무관한 만료 객체를 주기적으로 정리한다. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void cleanExpired() {
        // 원격 스토리지 I/O가 다른 업무의 공용 스케줄을 지연시키지 않게 한다.
        if (cleaning.compareAndSet(false, true)) {
            maintenance.submit(() -> {
                try {
                    deleteExpiredFiles();
                } finally {
                    cleaning.set(false);
                }
            });
        }
    }

    // 원본을 공유하는 재시도를 포함해 기한이 끝난 파일과 체크포인트를 지운다.
    private void deleteExpiredFiles() {
        Instant now = clock.instant();
        for (DocumentJob expired : repository.expiredWithFiles(now)) {
            try {
                remove(expired.inputKey());
                remove(expired.outputKey());
                for (String checkpoint : repository.checkpointKeys(expired.id())) {
                    remove(checkpoint);
                }
                repository.clearExpired(expired, now);
            } catch (IOException unavailable) {
                LOGGER.warn("Document cleanup deferred jobId={}", expired.id());
            }
        }
        repository.removeOldMetadata(now);
        try {
            writes.cleanOldOrphans(files);
        } catch (IOException unavailable) {
            LOGGER.warn("Document orphan cleanup deferred");
        }
    }

    // 현재 시도에서 사용한 임대와 로컬 사본을 종료 상태와 함께 정리한다.
    private void execute(DocumentJob job) {
        var pulse = heartbeat.scheduleAtFixedRate(() -> renew(job), 10, 10, TimeUnit.SECONDS);
        try {
            translate(job);
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
        Path source = Files.createTempFile("translacat-document-", ".source");
        Path output = null;
        try {
            // 비공개 저장소에서 원본의 크기와 지문을 확인한 뒤 처리기에 전달한다.
            files.copyVerified(job.inputKey(), source, job.inputBytes(), job.inputHash());
            DocumentFormatPort.Plan plan = read(job, source);
            List<List<DocumentFormatPort.Segment>> batches = split(plan.segments());
            int total = plan.segments().size();
            String model = job.modelRevision();
            advance(job, DocumentJob.State.TRANSLATING, total, 0, model);

            // 이미 검증한 배치는 시도 재개에만 사용한다.
            var completed = processBatches(job, batches, total, model);

            // 완성된 파일을 검증하고 같은 상태·리스에서 확인된 결과만 공개한다.
            advance(job, DocumentJob.State.REPACKING, total, total, completed.model());
            output = Files.createTempFile("translacat-document-", ".result");
            List<DocumentJob.Warning> warnings;
            try {
                warnings = warnings(plan.warnings(), formats.write(plan, completed.translatedPieces(), output));
            } catch (IOException invalid) {
                throw new JobFailure("OUTPUT_INTEGRITY_FAILED");
            }
            publish(job, output, warnings);
        } finally {
            DocumentTranslationService.deleteTemp(output);
            DocumentTranslationService.deleteTemp(source);
        }
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
        var batches = new ArrayList<List<DocumentFormatPort.Segment>>();
        var current = new ArrayList<DocumentFormatPort.Segment>();
        int documentsCharacters = 0;
        int batchCharacters = 0;
        for (var segment : segments) {
            int length = segment.pieces().stream()
                    .mapToInt(piece -> piece.text().codePointCount(0, piece.text().length())).sum();
            documentsCharacters += length;
            if (length > 2_000 || documentsCharacters > 30_000 || segments.size() > 2_000) {
                throw new JobFailure("DOCUMENT_LIMIT_EXCEEDED");
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

    // 배치 입력이 같을 때만 체크포인트를 재사용하고, 새 결과는 현재 토큰으로 기록한다.
    private Batch batch(DocumentJob job, int number, List<DocumentFormatPort.Segment> units)
            throws IOException, InterruptedException {
        checkRunning(job);
        String hash = HexFormat.of().formatHex(DocumentFiles.sha256().digest(
                mapper.writeValueAsBytes(units)));
        var checkpoint = repository.checkpoint(job.id(), number, hash);
        if (checkpoint.isPresent()) {
            try (var stored = files.open(checkpoint.get().key())) {
                return mapper.readValue(stored, Batch.class);
            }
        }

        Batch result = call(job, number, units);
        Path staged = Files.createTempFile("translacat-document-", ".batch");
        String key = "batch-" + job.id() + "-" + job.leaseToken() + "-" + number;
        try {
            mapper.writeValue(staged.toFile(), result);
            put(job, key, staged, DocumentJob.State.TRANSLATING);
            if (!repository.saveCheckpoint(job, number, hash, key, clock.instant())) {
                writes.removeOrphan(files, job.ownerId(), job.id(), key);
                throw new LostLease();
            }
        } finally {
            DocumentTranslationService.deleteTemp(staged);
        }
        return result;
    }

    // 제공자 실패는 작업 전체 마감 안에서 한 번 더 시도할 수 있다.
    private Batch call(DocumentJob job, int number, List<DocumentFormatPort.Segment> units)
            throws InterruptedException {
        Instant deadline = job.createdAt().plusSeconds(180);
        String code = "TRANSLATION_UNAVAILABLE";
        for (int attempt = 0; attempt < 2; attempt++) {
            if (!clock.instant().isBefore(deadline)) {
                throw new JobFailure("TRANSLATION_TIMEOUT");
            }
            checkRunning(job);
            try {
                return translator.translate(job.id(), number, job.sourceLanguage(), job.targetLanguage(),
                        units, deadline);
            } catch (DocumentTranslationGateway.Failure unavailable) {
                code = unavailable.reason() == DocumentTranslationGateway.Reason.TIMEOUT
                        ? "TRANSLATION_TIMEOUT" : "TRANSLATION_UNAVAILABLE";
                Duration delay = unavailable.retryAfter() == null ? Duration.ofMillis(300) : unavailable.retryAfter();
                if (attempt == 1 || !unavailable.retryable() || !clock.instant().plus(delay).isBefore(deadline)) {
                    throw new JobFailure(code);
                }
                Thread.sleep(delay);
            } catch (IOException unavailable) {
                if (attempt == 0) {
                    Thread.sleep(300);
                }
            }
        }
        throw new JobFailure(clock.instant().isBefore(deadline) ? code : "TRANSLATION_TIMEOUT");
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
        if (!now.isBefore(job.createdAt().plusSeconds(180))) {
            throw new JobFailure("TRANSLATION_TIMEOUT");
        }
        if (!repository.progress(job, stage, total, completed, model, now)) {
            throw new LostLease();
        }
    }

    // 현재 시도 전용 키에 보관한 다음 다운로드 가능 상태를 원자적으로 확정한다.
    private void publish(DocumentJob job, Path output, List<DocumentJob.Warning> warnings) throws IOException {
        String key = "result-" + job.id() + "-" + job.leaseToken();
        long bytes = Files.size(output);
        var digest = DocumentFiles.sha256();
        try (var input = new java.security.DigestInputStream(Files.newInputStream(output), digest)) {
            input.transferTo(java.io.OutputStream.nullOutputStream());
        }
        String hash = HexFormat.of().formatHex(digest.digest());
        put(job, key, output, DocumentJob.State.REPACKING);
        Instant now = clock.instant();
        if (!now.isBefore(job.createdAt().plusSeconds(180))) {
            writes.removeOrphan(files, job.ownerId(), job.id(), key);
            throw new JobFailure("TRANSLATION_TIMEOUT");
        }
        if (!repository.succeed(job, key, hash, bytes, warnings, now)) {
            writes.removeOrphan(files, job.ownerId(), job.id(), key);
            throw new LostLease();
        }
    }

    // 계정과 현재 시도의 권한을 함께 확인한 뒤 객체 쓰기 의도를 먼저 커밋한다.
    private void put(DocumentJob job, String key, Path source, DocumentJob.State state) throws IOException {
        if (!writes.store(files, job.ownerId(), job.id(), key, source,
                () -> repository.hasLease(job, state, clock.instant()))) {
            throw new LostLease();
        }
    }

    // 동일 경고는 발생 횟수와 최대 열 곳의 대표 위치로 반환한다.
    private static List<DocumentJob.Warning> warnings(List<DocumentFormatPort.Warning> extraction,
            List<DocumentFormatPort.Warning> writing) {
        Map<String, List<DocumentFormatPort.Warning>> byCode = java.util.stream.Stream.concat(
                extraction.stream(), writing.stream()).distinct().collect(Collectors.groupingBy(
                        DocumentFormatPort.Warning::code, LinkedHashMap::new, Collectors.toList()));
        return byCode.entrySet().stream().map(entry -> new DocumentJob.Warning(entry.getKey(),
                entry.getValue().size(), entry.getValue().stream().map(DocumentFormatPort.Warning::location)
                        .filter(location -> location != null && !location.isBlank())
                        .distinct().limit(10).map(location -> location.substring(0, Math.min(120, location.length())))
                        .toList())).toList();
    }

    // 저장소 청소는 누락된 키를 허용한다.
    private void remove(String key) throws IOException {
        if (key != null) {
            files.delete(key);
        }
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
