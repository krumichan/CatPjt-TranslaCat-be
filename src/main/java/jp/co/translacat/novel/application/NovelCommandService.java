package jp.co.translacat.novel.application;

import jakarta.annotation.PreDestroy;
import jp.co.translacat.novel.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class NovelCommandService {
    private final NovelRepository store;
    private final NovelQueryService query;
    private final NovelPorts.Ai ai;
    private final long deadlineMillis;
    private final FairAiScheduler scheduler;
    private final boolean ownsScheduler;
    private final ExecutorService coordinators = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore admittedJobs = new Semaphore(32);
    private final ConcurrentHashMap<String, FutureTask<Void>> runningJobs = new ConcurrentHashMap<>();
    private final Map<String, Map<String,String>> retainedResults = java.util.Collections.synchronizedMap(new LinkedHashMap<>());

    public NovelCommandService(NovelRepository store, NovelQueryService query, NovelPorts.Ai ai, long deadlineMillis) {
        this(store, query, ai, deadlineMillis, new FairAiScheduler(10, 256), true);
    }
    @Autowired
    public NovelCommandService(NovelRepository store, NovelQueryService query, NovelPorts.Ai ai,
                               @Value("${novel.translation.deadline-millis:90000}") long deadlineMillis,
                               FairAiScheduler scheduler) {
        this(store, query, ai, deadlineMillis, scheduler, false);
    }
    private NovelCommandService(NovelRepository store, NovelQueryService query, NovelPorts.Ai ai,
                                long deadlineMillis, FairAiScheduler scheduler, boolean ownsScheduler) {
        this.store = store;
        this.query = query;
        this.ai = ai;
        this.deadlineMillis = Math.clamp(deadlineMillis, 100, 600_000);
        this.scheduler = scheduler;
        this.ownsScheduler = ownsScheduler;
    }

    public ReaderSnapshot start(EpisodeKey key, long actorId, String revision, String idempotencyKey, boolean retry) {
        // 현재 원문과 actor의 용어집을 고정한 뒤 같은 정책으로 계획과 캐시 identity를 만든다.
        long started = System.nanoTime();
        SourceEpisode source = query.requireCurrentRevision(key, revision);
        NovelRepository.Glossary glossary = store.glossary(key, actorId);
        var selection = query.resolve(source);
        if (selection.options().lengthPolicy() == TranslationOptions.LengthPolicy.QUALITY_LENGTH_V1
                && (deadlineMillis != 300_000 || scheduler.globalLimit() != 10)) {
            throw new NovelProblem("LENGTH_POLICY_EXECUTION_LIMIT_MISMATCH", 503);
        }
        long planningStarted = System.nanoTime();
        var plan = new TranslationPlanner().planResolved(source, selection, !glossary.terms().isEmpty());
        double planningMs = RequestTrace.elapsed(planningStarted);
        RequestTrace.duration("planningMs", planningStarted);

        // 중복 요청은 저장된 작업을 공유한다. 거절 우회와 누적 호출 상한 초과는 lease 전에 막는다.
        String cacheKey = query.cacheKey(key, source, actorId, glossary.version(), selection);
        NovelRepository.Job job = store.create(key, source, actorId, cacheKey, idempotencyKey);
        // A의 부분 prefix는 마지막 provider 응답 검증을 통과한 캐시가 아니다.
        // 미완료 suffix만 재요청하면 서로 다른 응답을 합쳐 전체 성공으로 승격할 수 있다.
        if (ai.progressiveAEnabled() && retry && "PARTIAL".equals(job.state()))
            throw new NovelProblem("PROGRESSIVE_PARTIAL_RETRY_UNSUPPORTED", 409);
        if (retry && job.errors().values().stream().anyMatch(code -> code.contains("REFUS")))
            throw new NovelProblem("PROVIDER_REFUSAL", 422);
        if (job.providerCalls() >= 256 && !List.of("SUCCEEDED", "RUNNING").contains(job.state()))
            throw new NovelProblem("JOB_CALL_BUDGET_EXHAUSTED", 429);
        String traceId = RequestTrace.idOrNew();

        // DB lease 소유자만 coordinator를 등록하고 취소·거절 경합에서도 회차 슬롯을 한 번만 반환한다.
        store.claim(cacheKey, retry, deadlineMillis + 5000).ifPresent(owner -> {
            if (!admittedJobs.tryAcquire()) {
                store.releaseRejected(owner);
                throw new NovelProblem("TRANSLATION_QUEUE_FULL", 429, true, 1000);
            }
            long deadline = started + TimeUnit.MILLISECONDS.toNanos(deadlineMillis);
            var released = new java.util.concurrent.atomic.AtomicBoolean();
            Runnable release = () -> {
                if (released.compareAndSet(false, true)) admittedJobs.release();
            };
            try {
                var telemetry = new JobTelemetry(store, owner, traceId, started, plan, planningMs,
                        scheduler.globalLimit(), deadlineMillis, ai.executionIdentity());
                FutureTask<Void> work = new FutureTask<>(() -> {
                    execute(owner, source, glossary, plan, traceId, deadline, telemetry);
                    return null;
                }) {
                    private volatile boolean entered;
                    @Override public void run() {
                        entered = true;
                        try {
                            super.run();
                        } finally {
                            release.run();
                            runningJobs.remove(owner.id(), this);
                        }
                    }
                    @Override protected void done() {
                        if (!entered) release.run();
                        runningJobs.remove(owner.id(), this);
                    }
                };
                runningJobs.put(owner.id(), work);
                coordinators.execute(work);
            } catch (RuntimeException exception) {
                release.run();
                FutureTask<Void> rejected = runningJobs.remove(owner.id());
                if (rejected != null) rejected.cancel(false);
                store.releaseRejected(owner);
                throw exception;
            }
        });
        return query.snapshot(key, source, store.byCache(cacheKey).orElseThrow(), glossary.version());
    }

    public NovelRepository.Glossary glossary(EpisodeKey key, long actorId, String expectedVersion, Map<String, String> terms) {
        return store.saveGlossary(key, actorId, expectedVersion, terms);
    }

    public ReaderSnapshot repair(EpisodeKey key, long actorId, String jobId, String revision, String requestKey, String segmentId) {
        // client가 보낸 문장·상태를 사용하지 않는다. 현재 actor/원문/정책과 저장된 실패만 복구 대상이다.
        ReaderSnapshot snapshot = query.status(key, actorId, jobId);
        SourceEpisode source = query.requireCurrentRevision(key, revision);
        NovelRepository.Job job = store.byId(key, jobId, actorId);
        List<String> targets = segmentId == null ? snapshot.segments().stream()
                .filter(s -> s.status().equals("FAILED") && (s.errorCode() == null || !s.errorCode().contains("REFUS")))
                .map(ReaderSnapshot.Segment::id).toList() : List.of(segmentId);
        var claim = store.claimRepair(job, source, targets, requestKey, RequestTrace.idOrNew(), deadlineMillis + 5000);
        if (claim.claimed()) {
            var owner = claim.repair();
            if (!admittedJobs.tryAcquire()) {
                store.mergeRepair(owner, Map.of(), failureMap(owner.targetIds(), "TRANSLATION_QUEUE_FULL"), 0, 0);
                store.finishRepair(owner, source);
                throw new NovelProblem("TRANSLATION_QUEUE_FULL", 429);
            }
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(deadlineMillis);
            coordinators.submit(() -> {
                try { executeRepair(key, actorId, job, source, owner, deadline); }
                finally { admittedJobs.release(); }
            });
        }
        return query.status(key, actorId, jobId);
    }

    private void executeRepair(EpisodeKey key, long actor, NovelRepository.Job job, SourceEpisode source,
                               NovelRepository.Repair owner, long deadline) {
        Future<NovelPorts.Translation> call = null;
        long started = System.nanoTime();
        AtomicLong queueMillis = new AtomicLong(-1);
        var telemetry = new LinkedHashMap<String,Object>();
        telemetry.put("recoveryPolicyVersion", "selective-a-repair-v1");
        telemetry.put("profileIdentity", ai.executionIdentity());
        telemetry.put("contextNeighborDistance", 4); telemetry.put("contextCharacterLimit", 4000);
        telemetry.put("referenceLimit", 12); telemetry.put("referenceCharacterLimit", 8000);
        telemetry.put("providerFirstTokenMs", null); telemetry.put("traceId", owner.traceId());
        telemetry.put("callId", owner.id() + ":repair:0");
        try {
            // DB 저장만 실패한 검증 결과가 이 프로세스에 남아 있으면 외부 모델 없이 먼저 commit을 재시도한다.
            Map<String,String> retained = retained(job.cacheKey());
            Map<String,String> reusable = new LinkedHashMap<>();
            owner.targetIds().forEach(id -> { if (retained.containsKey(id)) reusable.put(id, retained.get(id)); });
            if (!reusable.isEmpty()) {
                long commit = System.nanoTime();
                if (!store.mergeRepair(owner, reusable, Map.of(), 0, 0)) return;
                telemetry.put("retainedCommitMs", RequestTrace.elapsed(commit));
                telemetry.put("retainedResultCount", reusable.size());
                forget(job.cacheKey(), reusable.keySet());
            }
            var targets = source.segments().stream().filter(s -> owner.targetIds().contains(s.id()) && !reusable.containsKey(s.id())).toList();
            if (targets.isEmpty()) return;
            var options = query.resolve(source).options();
            var glossary = store.glossary(key, actor);
            var references = new ArrayList<Map<String,String>>();
            var contextSegments = new ArrayList<SourceEpisode.Segment>();
            int contextChars = 0, referenceChars = 0;
            // 가까운 원문과 이미 저장된 번역은 참고자료이며 target ID 집합에는 포함하지 않는다.
            var nearest = source.segments().stream().filter(s -> !owner.targetIds().contains(s.id()))
                    .filter(s -> targets.stream().anyMatch(t -> Math.abs(t.order() - s.order()) <= 4))
                    .sorted(Comparator.comparingInt(s -> targets.stream().mapToInt(t -> Math.abs(t.order() - s.order())).min().orElse(0))).toList();
            for (var neighbor : nearest) {
                if (contextChars + neighbor.plainJa().length() <= 4000) { contextSegments.add(neighbor); contextChars += neighbor.plainJa().length(); }
                String accepted = job.results().get(neighbor.id());
                if (accepted != null && references.size() < 12 && referenceChars + neighbor.plainJa().length() + accepted.length() <= 8000) {
                    references.add(Map.of("source", neighbor.plainJa(), "translation", accepted));
                    referenceChars += neighbor.plainJa().length() + accepted.length();
                }
            }
            var context = contextSegments.stream().sorted(Comparator.comparingInt(SourceEpisode.Segment::order)).map(SourceEpisode.Segment::plainJa).toList();
            call = scheduler.submitRepair(job.id(), deadline, queueMillis::set, () -> {
                // 실제 dispatch 직전 policy와 DB 소유권을 재검사한다. 자동 유료 재시도는 하지 않는다.
                if (!query.cacheKey(key, query.requireCurrentRevision(key, source.revision()), actor, store.glossary(key, actor).version()).equals(job.cacheKey()))
                    throw new NovelProblem("TRANSLATION_POLICY_CHANGED", 409);
                if (!store.reserveRepairCall(owner)) throw new NovelProblem("REPAIR_LEASE_LOST", 409);
                return ai.repair(owner.traceId(), owner.id() + ":repair:0", targets, context, glossary.terms(), references,
                        Math.min(300_000, remaining(deadline)), options.responseShape(), options.validationPolicy(), options.annotationPolicy());
            });
            var translated = call.get(Math.max(1, remaining(deadline)), TimeUnit.MILLISECONDS);
            telemetry.putAll(translated.timings()); telemetry.put("inputTokens", translated.inputTokens()); telemetry.put("outputTokens", translated.outputTokens());
            long validationStarted = System.nanoTime();
            Set<String> expected = targets.stream().map(SourceEpisode.Segment::id).collect(java.util.stream.Collectors.toSet());
            if (!translated.items().keySet().equals(expected)) throw new NovelProblem("REPAIR_TARGET_MISMATCH", 502);
            TranslationContentGuard.validate(targets, translated.items(), options.validationPolicy());
            telemetry.put("novelValidationMs", RequestTrace.elapsed(validationStarted));
            remember(job.cacheKey(), translated.items());
            if (remaining(deadline) <= 0 || !query.cacheKey(key, query.requireCurrentRevision(key, source.revision()), actor,
                    store.glossary(key, actor).version()).equals(job.cacheKey())) throw new NovelProblem("REPAIR_LEASE_LOST", 409);
            long commit = System.nanoTime();
            if (store.mergeRepair(owner, translated.items(), Map.of(), translated.inputTokens(), translated.outputTokens())) forget(job.cacheKey(), translated.items().keySet());
            telemetry.put("commitMs", RequestTrace.elapsed(commit));
        } catch (Exception exception) {
            Throwable cause = exception instanceof ExecutionException ? exception.getCause() : exception;
            String code = cause instanceof NovelProblem p ? p.code() : cause instanceof org.springframework.dao.DataAccessException ? "DB_PERSISTENCE_FAILED"
                    : cause instanceof TimeoutException ? "JOB_DEADLINE_EXCEEDED" : "REPAIR_EXECUTION_FAILED";
            telemetry.put("errorCode", code);
            try { store.mergeRepair(owner, Map.of(), failureMap(owner.targetIds(), code), 0, 0); } catch (org.springframework.dao.DataAccessException ignored) { }
            if (call != null && !call.isDone()) call.cancel(true);
            if (cause instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally {
            try { store.finishRepair(owner, source); } catch (org.springframework.dao.DataAccessException ignored) { /* lease 만료 후 재연결에서 실패 확정 */ }
            telemetry.put("novelQueueMs", queueMillis.get() < 0 ? null : queueMillis.get());
            telemetry.put("dispatched", queueMillis.get() >= 0);
            telemetry.put("elapsedMs", RequestTrace.elapsed(started));
            try { store.saveRepairDiagnostics(owner, telemetry); } catch (org.springframework.dao.DataAccessException ignored) { /* 미관측 계측은 만들지 않는다. */ }
        }
    }

    private Map<String,String> failureMap(List<String> ids, String code) {
        Map<String,String> result = new HashMap<>(); ids.forEach(id -> result.put(id, code)); return result;
    }
    private void remember(String cache, Map<String,String> values) {
        synchronized (retainedResults) {
            if (!retainedResults.containsKey(cache) && retainedResults.size() >= 32) retainedResults.remove(retainedResults.keySet().iterator().next());
            var existing = retainedResults.computeIfAbsent(cache, ignored -> new HashMap<>());
            values.forEach(existing::putIfAbsent);
            if (existing.values().stream().mapToLong(String::length).sum() > 1_500_000) retainedResults.remove(cache);
        }
    }
    private Map<String,String> retained(String cache) { synchronized (retainedResults) { return Map.copyOf(retainedResults.getOrDefault(cache, Map.of())); } }
    private void forget(String cache, Set<String> ids) { synchronized (retainedResults) {
        var values = retainedResults.get(cache); if (values != null) { ids.forEach(values::remove); if (values.isEmpty()) retainedResults.remove(cache); }
    } }

    public ReaderSnapshot cancel(EpisodeKey key, long actorId, String jobId) {
        // actor·회차 소유권과 DB 취소 fence가 먼저 결정한다. 이미 완료된 성공은 유지한다.
        NovelRepository.Job existing = store.byId(key, jobId, actorId);
        SourceEpisode source = store.revision(key, existing.revision());
        NovelRepository.Job cancelled = store.cancel(key, jobId, actorId, source);
        if (cancelled.state().equals("CANCELLED")) {
            synchronized (retainedResults) { retainedResults.remove(cancelled.cacheKey()); }
            FutureTask<Void> running = runningJobs.get(jobId);
            if (running != null) running.cancel(true);
        }
        return query.snapshot(key, source, cancelled, store.glossary(key, actorId).version());
    }

    private void execute(NovelRepository.Job owner, SourceEpisode source, NovelRepository.Glossary glossary,
                         TranslationPlanner.Plan plan, String traceId, long deadline, JobTelemetry telemetry) {
        // 이전 성공은 다시 보내지 않고 계획된 청크의 미완료 target만 실행한다.
        List<Future<Boolean>> pending = new ArrayList<>();
        var missing = source.segments().stream().filter(s -> !owner.results().containsKey(s.id()) && !jp.co.translacat.novel.domain.SourceText.isBlank(s.plainJa())).toList();
        boolean providerVerified = true;
        try {
            for (var chunk : plan.chunks()) {
                var batch = chunk.targets().stream().filter(s -> !owner.results().containsKey(s.id())).toList();
                if (!batch.isEmpty()) pending.add(coordinators.submit(() -> translate(owner, batch, chunk, glossary,
                        plan.effectiveC(), TranslationOptions.ResponseShape.valueOf(plan.responseShape()),
                        TranslationOptions.ValidationPolicy.valueOf(plan.validationPolicy()),
                        TranslationOptions.AnnotationPolicy.valueOf(plan.annotationPolicy()), traceId, deadline, telemetry)));
            }
            for (Future<Boolean> future : pending) {
                if (remaining(deadline) <= 0) throw new TimeoutException();
                if (!future.get(remaining(deadline), TimeUnit.MILLISECONDS)) providerVerified = false;
            }
        } catch (Exception exception) {
            providerVerified = false;
            // 전체 기한·실행 실패는 남은 작업을 취소하고 기존 성공을 보존한 채 실패를 합친다.
            pending.forEach(future -> future.cancel(true));
            Map<String, String> errors = new HashMap<>();
            missing.forEach(segment -> errors.put(segment.id(), remaining(deadline) <= 0 ? "JOB_DEADLINE_EXCEEDED" : "JOB_EXECUTION_FAILED"));
            store.merge(owner, Map.of(), errors, 0, 0);
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally {
            // 마지막 상태 commit이 반환된 시점과 후속 계측 저장을 구분한다.
            long commitStarted = System.nanoTime();
            store.finish(owner, source, providerVerified);
            double commitMs = RequestTrace.elapsed(commitStarted);
            boolean durable = store.byCache(owner.cacheKey()).map(j -> j.state().equals("SUCCEEDED")).orElse(false);
            telemetry.completed(commitMs, durable);
        }
    }

    private boolean translate(NovelRepository.Job owner, List<SourceEpisode.Segment> batch, TranslationPlanner.Chunk chunk,
                           NovelRepository.Glossary glossary, int concurrency, TranslationOptions.ResponseShape shape,
                           TranslationOptions.ValidationPolicy validation, TranslationOptions.AnnotationPolicy annotation,
                           String traceId, long deadline, JobTelemetry telemetry) {
        // 재시도는 청크당 최대 한 번이며 각 시도의 대기·실행·실패를 별도 계측한다.
        NovelProblem failure = new NovelProblem("JOB_DEADLINE_EXCEEDED", 504);
        var progressivePublished = new java.util.concurrent.atomic.AtomicBoolean();
        for (int attempt = 0; attempt < 2; attempt++) {
            if (remaining(deadline) < 100 || Thread.currentThread().isInterrupted()) break;
            String callId = owner.id() + ":" + chunk.index() + ":" + attempt;
            AtomicLong queuedMs = new AtomicLong(-1);
            Map<String, Object> span = new LinkedHashMap<>();
            span.put("callId", callId);
            span.put("attempt", attempt);
            span.put("providerTtftMs", null);
            long attemptStarted = System.nanoTime();
            Future<NovelPorts.Translation> call = null;
            try {
                // 회차별·전체 슬롯을 얻은 뒤 DB fence와 호출 예산을 재검사하고 외부 실행한다.
                call = scheduler.submit(owner.id(), concurrency, deadline, queuedMs::set, () -> {
                    requireCurrentPolicy(owner);
                    if (!store.reserveCall(owner)) {
                        boolean exhausted = store.byCache(owner.cacheKey()).map(job -> job.providerCalls() >= 256).orElse(false);
                        throw new NovelProblem(exhausted ? "JOB_CALL_BUDGET_EXHAUSTED" : "JOB_LEASE_LOST", 409);
                    }
                    var context = chunk.context().stream().map(SourceEpisode.Segment::plainJa).toList();
                    if (!ai.progressiveAEnabled()) return ai.translate(traceId, callId, batch, context,
                            glossary.terms(), Math.min(300_000, remaining(deadline)), shape, validation, annotation);
                    return ai.translateProgressive(traceId, callId, batch, context, glossary.terms(),
                            Math.min(300_000, remaining(deadline)), shape, validation, annotation, (segment, text) -> {
                                TranslationContentGuard.validate(List.of(segment), Map.of(segment.id(), text), validation);
                                requireCurrentPolicy(owner);
                                if (remaining(deadline) <= 0 || Thread.currentThread().isInterrupted()) {
                                    throw new NovelProblem("JOB_LEASE_LOST", 409);
                                }
                                remember(owner.cacheKey(), Map.of(segment.id(), text));
                                try {
                                    if (!store.merge(owner, Map.of(segment.id(), text), Map.of(), 0, 0)) {
                                        forget(owner.cacheKey(), Set.of(segment.id()));
                                        throw new NovelProblem("JOB_LEASE_LOST", 409);
                                    }
                                    forget(owner.cacheKey(), Set.of(segment.id()));
                                } catch (org.springframework.dao.DataAccessException failedCommit) {
                                    // 완전히 검증된 결과는 제한된 메모리에 보존하고 다음 문장 수신을 계속한다.
                                }
                                span.putIfAbsent("novelFirstSentenceMs", RequestTrace.elapsed(attemptStarted));
                                progressivePublished.set(true);
                            }, (segment, code) -> store.merge(owner, Map.of(), Map.of(segment.id(), code), 0, 0));
                });
                NovelPorts.Translation translated = call.get(Math.max(1, remaining(deadline)), TimeUnit.MILLISECONDS);

                // 최소 정보 검증과 남은 기한 검사 후 현재 lease 소유자의 결과만 저장한다.
                TranslationContentGuard.validate(ai.progressiveAEnabled() ? batch.stream().filter(s -> translated.items().containsKey(s.id())).toList() : batch, translated.items(), validation);
                span.putAll(translated.timings());
                span.put("inputTokens", translated.inputTokens());
                span.put("outputTokens", translated.outputTokens());
                if (remaining(deadline) <= 0 || Thread.currentThread().isInterrupted())
                    throw new NovelProblem("JOB_DEADLINE_EXCEEDED", 504);
                long commitStarted = System.nanoTime();
                requireCurrentPolicy(owner);
                boolean accepted = store.merge(owner, translated.items(), Map.of(), translated.inputTokens(), translated.outputTokens());
                if (accepted) forget(owner.cacheKey(), translated.items().keySet());
                double commitMs = RequestTrace.elapsed(commitStarted);
                span.put("status", accepted ? "SUCCEEDED" : "FENCED");
                telemetry.chunkCommitted(chunk.index(), commitMs, accepted);
                return accepted && translated.items().size() == batch.size();
            } catch (Exception exception) {
                // 안전한 오류 코드로 정규화한다. 거절·비재시도 오류·기한 부족은 즉시 끝낸다.
                Throwable cause = exception instanceof ExecutionException ? exception.getCause() : exception;
                failure = cause instanceof NovelProblem problem ? problem
                        : cause instanceof TimeoutException ? new NovelProblem("JOB_DEADLINE_EXCEEDED", 504)
                        : cause instanceof InterruptedException ? new NovelProblem("JOB_INTERRUPTED", 503)
                        : new NovelProblem("AI_EXECUTION_FAILED", 502);
                span.put("status", "FAILED");
                span.put("errorCode", failure.code());
                span.put("retryAfterMs", failure.retryAfterMillis());
                if (call != null && !call.isDone()) call.cancel(true);
                if (cause instanceof InterruptedException) Thread.currentThread().interrupt();
                long wait = Math.max(50, failure.retryAfterMillis());
                if (ai.progressiveAEnabled() || progressivePublished.get() || !failure.retryable() || attempt == 1 || failure.code().contains("REFUS") || remaining(deadline) <= wait + 500) break;
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    failure = new NovelProblem("JOB_INTERRUPTED", 503);
                    break;
                }
            } finally {
                // 슬롯 거절과 실제 dispatch를 구분하며 관측하지 않은 첫 토큰 값은 만들지 않는다.
                span.put("dispatched", queuedMs.get() >= 0);
                span.put("novelQueueMs", queuedMs.get() >= 0 ? queuedMs.get() : RequestTrace.elapsed(attemptStarted));
                span.put("attemptElapsedMs", RequestTrace.elapsed(attemptStarted));
                telemetry.attempt(chunk.index(), span);
            }
        }
        // 종료된 실패도 같은 lease/fence 조건으로 합쳐 취소 후 늦은 저장을 막는다.
        Map<String, String> errors = new HashMap<>();
        String code = failure.code();
        var retained = retained(owner.cacheKey());
        batch.forEach(segment -> errors.put(segment.id(), retained.containsKey(segment.id()) ? "DB_PERSISTENCE_FAILED" : code));
        store.merge(owner, Map.of(), errors, 0, 0);
        return false;
    }

    private long remaining(long deadline) { return Math.max(0, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())); }

    private void requireCurrentPolicy(NovelRepository.Job owner) {
        String[] parts = owner.sourceKey().split(":", -1);
        var key = new EpisodeKey(parts[0], parts[1], parts[2]);
        var source = query.requireCurrentRevision(key, owner.revision());
        if (!query.cacheKey(key, source, owner.actorId(), store.glossary(key, owner.actorId()).version()).equals(owner.cacheKey()))
            throw new NovelProblem("TRANSLATION_POLICY_CHANGED", 409);
    }

    @PreDestroy public void close() {
        coordinators.shutdownNow();
        if (ownsScheduler) scheduler.close();
    }
}
