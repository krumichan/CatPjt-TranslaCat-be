package jp.co.translacat.novel;

import jp.co.translacat.novel.application.FairAiScheduler;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class FairAiSchedulerTest {
    @Test
    void perEpisodeLimitsAndGlobalTenDoNotHideTwoSlotExecution() throws Exception {
        try (var scheduler = new FairAiScheduler(10, 200)) {
            var release = new CountDownLatch(1);
            var started = new CountDownLatch(10);
            AtomicInteger active = new AtomicInteger(), max = new AtomicInteger();
            var jobs = new ArrayList<Future<Integer>>();
            for (int episode = 0; episode < 2; episode++) {
                for (int chunk = 0; chunk < 10; chunk++) {
                    jobs.add(scheduler.submit("e" + episode, 5, deadline(), wait -> {}, () -> {
                        int value = active.incrementAndGet(); max.accumulateAndGet(value, Math::max);
                        started.countDown(); release.await(2, TimeUnit.SECONDS); active.decrementAndGet(); return value;
                    }));
                }
            }
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(max.get()).isEqualTo(10);
            assertThat(scheduler.activeFor("e0")).isEqualTo(5);
            assertThat(scheduler.activeFor("e1")).isEqualTo(5);
            release.countDown();
            for (Future<Integer> job : jobs) job.get(3, TimeUnit.SECONDS);
            assertThat(scheduler.maximumObserved()).isEqualTo(10);
        }
    }

    @Test
    void roundRobinDoesNotLetLongEpisodeStarveNewEpisodeAndCancellationDoesNotStartPaidWork() throws Exception {
        try (var scheduler = new FairAiScheduler(1, 6)) {
            var release = new CountDownLatch(1);
            List<String> order = new CopyOnWriteArrayList<>();
            var running = scheduler.submit("long", 1, deadline(), wait -> {}, () -> { release.await(); order.add("first"); return 0; });
            var a = scheduler.submit("long", 1, deadline(), wait -> {}, () -> { order.add("a"); return 1; });
            var b = scheduler.submit("long", 1, deadline(), wait -> {}, () -> { order.add("b"); return 2; });
            var shortJob = scheduler.submit("short", 1, deadline(), wait -> {}, () -> { order.add("short"); return 3; });
            var canceled = scheduler.submit("cancel", 1, deadline(), wait -> {}, () -> { throw new AssertionError("Canceled queued request must not call provider"); });
            assertThat(canceled.cancel(true)).isTrue();
            release.countDown(); running.get(2, TimeUnit.SECONDS); a.get(2, TimeUnit.SECONDS); b.get(2, TimeUnit.SECONDS); shortJob.get(2, TimeUnit.SECONDS);
            assertThat(order.indexOf("short")).isLessThan(order.indexOf("b"));
        }
    }

    @Test
    void queueIsBoundedAndExpiredRequestsNeverStartProvider() throws Exception {
        try (var scheduler = new FairAiScheduler(1, 1)) {
            var release = new CountDownLatch(1);
            var active = scheduler.submit("a", 1, deadline(), wait -> {}, () -> { release.await(); return 1; });
            var expired = scheduler.submit("b", 1, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(20), wait -> {}, () -> { throw new AssertionError(); });
            assertThatThrownBy(() -> scheduler.submit("c", 1, deadline(), wait -> {}, () -> 1)).hasMessage("AI_QUEUE_FULL");
            Thread.sleep(40); release.countDown(); active.get(2, TimeUnit.SECONDS);
            assertThatThrownBy(() -> expired.get(2, TimeUnit.SECONDS)).hasCauseInstanceOf(jp.co.translacat.novel.domain.NovelProblem.class);
        }
    }
    @Test
    void pausedAudioCannotLeaveDeferredGenerationBehindBusyTranslation() throws Exception {
        try (var scheduler = new FairAiScheduler(1, 4)) {
            var release = new CountDownLatch(1);
            var translation = scheduler.submit("episode", 1, deadline(), wait -> {}, () -> { release.await(); return 1; });
            AtomicInteger speech = new AtomicInteger();
            assertThatThrownBy(() -> scheduler.submitImmediate("audio", 2, deadline(), wait -> {}, speech::incrementAndGet))
                    .hasMessage("AUDIO_QUEUE_FULL");
            release.countDown(); translation.get(2, TimeUnit.SECONDS);
            assertThat(speech.get()).isZero();
        }
    }
    private long deadline() { return System.nanoTime() + TimeUnit.SECONDS.toNanos(10); }
    @Test void fiftyCatalogItemsLeaveRepairSlotAndCancelledCatalogNeverDispatches() throws Exception {
        try (var scheduler = new FairAiScheduler(4, 100)) {
            var release = new CountDownLatch(1);
            var entered = new CountDownLatch(3);
            List<Future<Integer>> catalog = new ArrayList<>();
            AtomicInteger calls = new AtomicInteger();
            for (int i = 0; i < 50; i++) catalog.add(scheduler.submitCatalog("list", 4, deadline(), ignored -> {}, () -> {
                calls.incrementAndGet(); entered.countDown(); release.await(); return 1;
            }));
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(scheduler.submitRepair("reader-hole", deadline(), ignored -> {}, () -> 20).get(1, TimeUnit.SECONDS)).isEqualTo(20);
            for (int i = 3; i < 50; i++) catalog.get(i).cancel(false);
            release.countDown();
            for (int i = 0; i < 3; i++) catalog.get(i).get(2, TimeUnit.SECONDS);
            assertThat(calls.get()).isEqualTo(3);
            assertThat(scheduler.maximumObserved()).isEqualTo(4);
        }
    }
    @Test void continuousRepairQueueYieldsToNormalWorkAfterThreeStarts() throws Exception {
        try (var scheduler = new FairAiScheduler(1, 30)) {
            var release = new CountDownLatch(1);
            var active = scheduler.submit("hold", 1, deadline(), ignored -> {}, () -> { release.await(); return 0; });
            List<String> order = new CopyOnWriteArrayList<>();
            List<Future<Integer>> tasks = new ArrayList<>();
            tasks.add(scheduler.submit("normal", 1, deadline(), ignored -> {}, () -> { order.add("normal"); return 0; }));
            for (int i = 0; i < 8; i++) tasks.add(scheduler.submitRepair("r" + i, deadline(), ignored -> {}, () -> { order.add("repair"); return 0; }));
            release.countDown(); active.get(); for (var task : tasks) task.get(2, TimeUnit.SECONDS);
            assertThat(order.indexOf("normal")).isEqualTo(3);
        }
    }
}
