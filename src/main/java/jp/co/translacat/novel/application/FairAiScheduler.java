package jp.co.translacat.novel.application;

import jakarta.annotation.PreDestroy;
import jp.co.translacat.novel.domain.NovelProblem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;

/** Novel 번역/음성이 공유하는 유한 대기열. 회차별 상한과 프로세스 전체 상한을 따로 적용한다. */
@Component
public final class FairAiScheduler implements AutoCloseable {
    private final int globalLimit;
    private final int queueLimit;
    private final ExecutorService workers;
    private final Map<String, Group> groups = new HashMap<>();
    private final ArrayDeque<String> rotation = new ArrayDeque<>();
    private int active;
    private int waiting;
    private int maximum;
    private int catalogActive;
    private int repairBurst;
    private boolean closed;
    private enum Priority { REPAIR, NORMAL, CATALOG }

    @Autowired
    public FairAiScheduler(@Value("${novel.ai.global-concurrency:10}") int globalLimit,
                           @Value("${novel.ai.queue-capacity:256}") int queueLimit) {
        if (globalLimit < 1 || globalLimit > 10 || queueLimit < 1 || queueLimit > 4096) {
            throw new IllegalArgumentException("AI_SCHEDULER_CONFIGURATION_INVALID");
        }
        this.globalLimit = globalLimit;
        this.queueLimit = queueLimit;
        this.workers = Executors.newFixedThreadPool(globalLimit, Thread.ofPlatform().daemon().factory());
    }

    private static final class Group {
        int active;
        final ArrayDeque<Work<?>> queue = new ArrayDeque<>();
    }

    private final class Work<T> extends FutureTask<T> {
        final String group;
        final int limit;
        final long deadline;
        final long enqueuedAt = System.nanoTime();
        final LongConsumer onStart;
        final Priority priority;
        boolean dispatched;
        Work(String group, int limit, long deadline, LongConsumer onStart, Callable<T> action, Priority priority) {
            super(action); this.group = group; this.limit = limit; this.deadline = deadline; this.onStart = onStart; this.priority = priority;
        }
        void expire() { setException(new NovelProblem("JOB_DEADLINE_EXCEEDED", 504)); }
        @Override protected void done() {
            // 취소된 실제 HTTP가 아직 종료 중이면 active 슬롯은 worker finally까지 유지한다.
            synchronized (FairAiScheduler.this) {
                Group state = groups.get(group);
                if (!dispatched && state != null && state.queue.remove(this)) {
                    waiting--; removeEmpty(group, state); drain();
                }
            }
        }
    }

    public synchronized <T> Future<T> submit(String groupId, int perEpisodeLimit, long deadline,
                                              LongConsumer onStart, Callable<T> action) {
        return enqueue(groupId, perEpisodeLimit, deadline, onStart, action, Priority.NORMAL);
    }

    public synchronized <T> Future<T> submitCatalog(String groupId, int limit, long deadline, LongConsumer onStart, Callable<T> action) {
        return enqueue("catalog:" + groupId, Math.min(4, limit), deadline, onStart, action, Priority.CATALOG);
    }

    public synchronized <T> Future<T> submitRepair(String groupId, long deadline, LongConsumer onStart, Callable<T> action) {
        return enqueue("repair:" + groupId, 1, deadline, onStart, action, Priority.REPAIR);
    }

    private <T> Future<T> enqueue(String groupId, int perEpisodeLimit, long deadline,
                                  LongConsumer onStart, Callable<T> action, Priority priority) {
        if (closed) throw new NovelProblem("AI_SCHEDULER_CLOSED", 503);
        if (groupId == null || groupId.isBlank() || perEpisodeLimit < 1 || perEpisodeLimit > 10) {
            throw new IllegalArgumentException("AI_SCHEDULER_REQUEST_INVALID");
        }
        int admissionLimit = priority == Priority.REPAIR ? queueLimit : Math.max(1, queueLimit - Math.min(4, queueLimit / 4));
        if (waiting >= admissionLimit) throw new NovelProblem("AI_QUEUE_FULL", 429, true, 1000);
        Group group = groups.computeIfAbsent(groupId, ignored -> new Group());
        Work<T> work = new Work<>(groupId, perEpisodeLimit, deadline, onStart, action, priority);
        if (group.queue.isEmpty()) rotation.addLast(groupId);
        group.queue.addLast(work); waiting++;
        drain();
        return work;
    }

    public synchronized <T> Future<T> submitImmediate(String groupId, int perEpisodeLimit, long deadline,
                                                       LongConsumer onStart, Callable<T> action) {
        // 음성 요청을 대기열에 남겨 pause/이동 뒤 새 합성을 시작하지 않는다.
        if (closed || active >= globalLimit || waiting > 0 || activeFor(groupId) >= perEpisodeLimit) {
            throw new NovelProblem("AUDIO_QUEUE_FULL", 429, true, 1000);
        }
        return submit(groupId, perEpisodeLimit, deadline, onStart, action);
    }

    private void drain() {
        int skipped = 0;
        while (!closed && active < globalLimit && !rotation.isEmpty() && skipped < rotation.size()) {
            // 최대 세 번의 복구 우선 실행 뒤 일반 작업에도 차례를 준다. 목록은 한 슬롯을 점유하지 않는다.
            String preferred = rotation.stream().filter(id -> {
                var g = groups.get(id); var w = g.queue.peekFirst();
                return g.active < w.limit && (w.priority != Priority.CATALOG || catalogActive < Math.max(1, globalLimit - 1))
                        && (repairBurst < 3 ? w.priority == Priority.REPAIR : w.priority != Priority.REPAIR);
            }).findFirst().orElse(null);
            if (preferred != null) { rotation.remove(preferred); rotation.addFirst(preferred); }
            String id = rotation.removeFirst();
            Group group = groups.get(id);
            Work<?> work = group.queue.peekFirst();
            if (group.active >= work.limit || work.priority == Priority.CATALOG && catalogActive >= Math.max(1, globalLimit - 1)) { rotation.addLast(id); skipped++; continue; }
            group.queue.removeFirst(); waiting--; work.dispatched = true;
            if (!group.queue.isEmpty()) rotation.addLast(id);
            if (work.deadline <= System.nanoTime() || work.isCancelled()) {
                if (!work.isDone()) work.expire();
                removeEmpty(id, group); skipped = 0; continue;
            }
            group.active++; active++; maximum = Math.max(maximum, active); skipped = 0;
            if (work.priority == Priority.CATALOG) catalogActive++;
            repairBurst = work.priority == Priority.REPAIR ? repairBurst + 1 : 0;
            workers.execute(() -> {
                try {
                    if (work.deadline <= System.nanoTime()) work.expire();
                    else { work.onStart.accept(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - work.enqueuedAt)); work.run(); }
                } finally {
                    synchronized (FairAiScheduler.this) {
                        active--; group.active--; if (work.priority == Priority.CATALOG) catalogActive--; removeEmpty(id, group); drain();
                    }
                }
            });
        }
    }

    private void removeEmpty(String id, Group group) {
        if (group.queue.isEmpty()) rotation.remove(id);
        if (group.active == 0 && group.queue.isEmpty()) groups.remove(id, group);
    }

    public synchronized int activeFor(String id) { return groups.containsKey(id) ? groups.get(id).active : 0; }
    public synchronized int maximumObserved() { return maximum; }
    public int globalLimit() { return globalLimit; }

    @Override @PreDestroy
    public synchronized void close() {
        closed = true;
        var queued = groups.values().stream().flatMap(g -> g.queue.stream()).toList();
        queued.forEach(task -> task.cancel(true));
        workers.shutdownNow();
    }
}
