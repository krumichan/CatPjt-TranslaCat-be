package jp.co.translacat.novel;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.novel.application.NovelRepository;
import jp.co.translacat.novel.domain.EpisodeKey;
import jp.co.translacat.novel.domain.SentenceSegmenter;
import jp.co.translacat.novel.domain.SourceEpisode;
import jp.co.translacat.novel.infrastructure.persistence.NovelSharedCache;
import jp.co.translacat.novel.infrastructure.persistence.NovelStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NovelSharedCacheTest {
    private final EpisodeKey key = new EpisodeKey("syosyetu", "n123aa", "1");
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager manager;
    private NovelStore store;
    private NovelSharedCache shared;
    private SourceEpisode source;

    @BeforeEach
    void prepare() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("novel-embedded-h2.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        manager = new DataSourceTransactionManager(dataSource);
        var json = new ObjectMapper();
        store = new NovelStore(jdbc, manager, json);
        shared = new NovelSharedCache(jdbc, new TransactionTemplate(manager), json, store);
        source = new SentenceSegmenter().segment(key, "題", List.of("猫。犬。鳥。"), null, null);
        store.saveSource(key, source, System.currentTimeMillis());
    }

    @Test
    void completedResultIsCopiedToPrivateJobWithoutCopyingCostOrTrace() {
        // 준비: 한 계정의 검증·저장이 완료된 번역을 공통 identity에 둔다.
        var owner = owner(11, "first", "same-policy");
        assertThat(store.reserveCall(owner)).isTrue();
        store.merge(owner, values(), Map.of(), 30, 20);
        store.saveDiagnostics(owner, Map.of("traceId", "producer-private-trace"));
        store.finish(owner, source);

        // 실행: 다른 계정이 같은 내용의 번역을 조회할 자기 작업을 만든다.
        var follower = shared.attach("same-policy", create(22, "second"), source);

        // 검증: 결과는 재사용하되 공개 작업 ID·권한·과금·trace는 자기 계정 범위다.
        assertThat(follower.state()).isEqualTo("SUCCEEDED");
        assertThat(follower.results()).containsExactlyInAnyOrderEntriesOf(values());
        assertThat(follower.actorId()).isEqualTo(22);
        assertThat(follower.id()).isNotEqualTo(owner.id());
        assertThat(follower.providerCalls()).isZero();
        assertThat(follower.inputTokens()).isZero();
        assertThat(follower.outputTokens()).isZero();
        assertThat(store.diagnostics(follower)).isEmpty();
        assertThatThrownBy(() -> store.byId(key, owner.id(), 22)).hasMessage("JOB_NOT_FOUND");
        long sequence = follower.eventSequence();
        assertThat(shared.attach("same-policy", follower, source).eventSequence()).isEqualTo(sequence);
    }

    @Test
    void concurrentAccountsAndRepositoryInstancesClaimOneProducer() throws Exception {
        // 준비: 같은 DB를 사용하는 두 저장소·계정의 작업을 미리 연결한다.
        var first = shared.attach("race", create(11, "race-first"), source);
        var second = shared.attach("race", create(22, "race-second"), source);
        var otherStore = new NovelStore(jdbc, manager, new ObjectMapper());
        var start = new CountDownLatch(1);

        // 실행: 서로 다른 계정의 명시 시작이 동시에 같은 공통 행을 claim한다.
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var one = workers.submit(() -> {
                start.await();
                return store.claim(first.cacheKey(), false, 60_000);
            });
            var two = workers.submit(() -> {
                start.await();
                return otherStore.claim(second.cacheKey(), false, 60_000);
            });
            start.countDown();
            var attempts = List.of(one.get(5, TimeUnit.SECONDS), two.get(5, TimeUnit.SECONDS));

            // 검증: 다른 계정·인스턴스에도 실제 dispatch 소유자는 한 명뿐이다.
            assertThat(attempts.stream().filter(Optional::isPresent)).hasSize(1);
            var winner = attempts.stream().flatMap(Optional::stream).findFirst().orElseThrow();
            assertThat(store.reserveCall(winner)).isTrue();
            assertThat(jdbc.queryForObject("SELECT SUM(provider_calls) FROM novel_translation_job", Integer.class)).isEqualTo(1);
        }
    }

    @Test
    void followerReceivesDurableProgressAndKeepsItsOwnSequence() {
        // 준비: 한 계정만 실행하고 다른 계정은 같은 producer를 기다린다.
        var owner = owner(11, "progress-first", "progress");
        var follower = shared.attach("progress", create(22, "progress-second"), source);
        assertThat(store.claim(follower.cacheKey(), false, 60_000)).isEmpty();

        // 실행: provider에서 완성·검증된 문장 하나만 먼저 저장한다.
        store.merge(owner, Map.of(id(0), "고양이"), Map.of(), 1, 1);
        var copied = shared.sync(follower, source);

        // 검증: prefix도 자기 DB 행에 있고 완료나 추가 과금으로 오인하지 않는다.
        assertThat(copied.state()).isEqualTo("RUNNING");
        assertThat(copied.results()).containsOnlyKeys(id(0));
        assertThat(copied.owner()).isNull();
        assertThat(copied.leaseUntil()).isEqualTo(owner.leaseUntil());
        assertThat(copied.providerCalls()).isZero();
        assertThat(shared.sync(copied, source).eventSequence()).isEqualTo(copied.eventSequence());
        assertThat(store.byCache(follower.cacheKey()).orElseThrow().results()).containsEntry(id(0), "고양이");
    }

    @Test
    void cancelledFollowerCannotBeRevivedByProducerCompletion() {
        // 준비: 다른 계정의 실행을 기다리는 자기 작업을 취소한다.
        var owner = owner(11, "cancel-first", "cancel");
        var follower = shared.attach("cancel", create(22, "cancel-second"), source);
        store.claim(follower.cacheKey(), false, 60_000);
        store.cancel(key, follower.id(), 22, source);

        // 실행: 원래 producer는 정상적으로 전체 번역을 마친다.
        store.merge(owner, values(), Map.of(), 0, 0);
        store.finish(owner, source);
        var cancelled = shared.attach("cancel", store.byCache(follower.cacheKey()).orElseThrow(), source);

        // 검증: 공유 완료 이벤트는 사용자의 취소를 되돌리지 않는다.
        assertThat(cancelled.state()).isEqualTo("CANCELLED");
        assertThat(cancelled.results()).isEmpty();
        assertThat(shared.sync(cancelled, source).state()).isEqualTo("CANCELLED");
    }

    @Test
    void explicitResumeUsesReadyCacheWithoutErasingCancellationHistory() {
        // 준비: 취소된 자기 작업과 그 뒤 완료된 다른 계정의 공통 번역이 있다.
        var owner = owner(11, "resume-first", "resume");
        var follower = shared.attach("resume", create(22, "resume-second"), source);
        store.claim(follower.cacheKey(), false, 60_000);
        var cancelled = store.cancel(key, follower.id(), 22, source);
        store.merge(owner, values(), Map.of(), 0, 0);
        store.finish(owner, source);

        // 실행: background 조회는 취소를 유지하고 명시적인 재개만 완료 캐시를 받는다.
        assertThat(shared.attach("resume", cancelled, source).state()).isEqualTo("CANCELLED");
        var resumed = shared.attach("resume", cancelled, source, true);

        // 검증: 유료 호출 없이 같은 작업의 결과를 복원하되 최초 취소 이력은 남는다.
        assertThat(resumed.state()).isEqualTo("SUCCEEDED");
        assertThat(resumed.id()).isEqualTo(cancelled.id());
        assertThat(resumed.results()).containsExactlyInAnyOrderEntriesOf(values());
        assertThat(resumed.providerCalls()).isZero();
        assertThat(store.initialAttemptState(resumed)).isEqualTo("CANCELLED");
    }

    @Test
    void cancelledProducerEndsFollowerWithoutAutomaticallyClaimingAgain() {
        // 준비: producer의 정상 prefix가 저장된 뒤 두 번째 계정이 대기한다.
        var owner = owner(11, "producer-cancel-first", "producer-cancel");
        store.merge(owner, Map.of(id(0), "고양이"), Map.of(), 0, 0);
        var follower = shared.attach("producer-cancel", create(22, "producer-cancel-second"), source);
        store.claim(follower.cacheKey(), false, 60_000);

        // 실행: producer 사용자가 취소한 후 follower는 저장 결과만 확인한다.
        store.cancel(key, owner.id(), 11, source);
        var stopped = shared.sync(follower, source);

        // 검증: 이미 정상인 문장은 보존하며 남은 구간은 실패로 표시한다.
        assertThat(stopped.state()).isEqualTo("PARTIAL");
        assertThat(stopped.results()).containsExactlyEntriesOf(Map.of(id(0), "고양이"));
        assertThat(stopped.errors()).containsKeys(id(1), id(2));
        assertThat(stopped.providerCalls()).isZero();
        assertThat(shared.completed("producer-cancel")).isEmpty();
    }

    @Test
    void completeLookingPrefixWithoutProviderVerificationIsNotSharedSuccess() {
        // 준비: 모든 문장은 도착했지만 provider 최종 응답 검증이 실패했다.
        var owner = owner(11, "unverified-first", "unverified");
        var follower = shared.attach("unverified", create(22, "unverified-second"), source);
        store.claim(follower.cacheKey(), false, 60_000);
        store.merge(owner, values(), Map.of(), 0, 0);
        store.finish(owner, source, false);

        // 실행: follower가 이 prefix를 재사용한다.
        var partial = shared.sync(follower, source);

        // 검증: 문장 표시와 회차 전체 성공을 분리한다.
        assertThat(partial.results()).hasSize(3);
        assertThat(partial.state()).isEqualTo("PARTIAL");
        assertThat(shared.completed("unverified")).isEmpty();
    }

    @Test
    void providerRefusalCannotBeBypassedByChangingAccounts() {
        // 준비: 다른 계정의 과거 부분 정상값이 있고 공통 producer는 거절로 끝났다.
        var legacy = create(33, "refusal-existing-partial");
        var legacyOwner = store.claim(legacy.cacheKey(), false, 60_000).orElseThrow();
        store.merge(legacyOwner, Map.of(id(0), "기존 정상 번역"), Map.of(id(1), "TAIL"), 0, 0);
        store.finish(legacyOwner, source, false);
        var owner = owner(11, "refusal-first", "refusal");
        store.reserveCall(owner);
        store.merge(owner, Map.of(), Map.of(id(0), "PROVIDER_REFUSAL", id(1), "PROVIDER_REFUSAL", id(2), "PROVIDER_REFUSAL"), 1, 0);
        store.finish(owner, source, false);

        // 실행: 다른 계정도 같은 입력을 명시적으로 다시 시작하려고 한다.
        var follower = shared.attach("refusal", create(22, "refusal-second"), source);

        // 검증: 거절 이유를 자기 작업에 표시하고 추가 유료 claim을 막는다.
        assertThat(follower.state()).isEqualTo("FAILED");
        assertThat(follower.errors()).hasSize(3).allSatisfy((id, error) -> assertThat(error).isEqualTo("PROVIDER_REFUSAL"));
        assertThat(follower.providerCalls()).isZero();
        assertThatThrownBy(() -> store.claim(follower.cacheKey(), true, 60_000)).hasMessage("PROVIDER_REFUSAL");
        var prior = shared.attach("refusal", store.byCache(legacy.cacheKey()).orElseThrow(), source);
        assertThat(prior.state()).isEqualTo("PARTIAL");
        assertThat(prior.results()).containsEntry(id(0), "기존 정상 번역");
        assertThatThrownBy(() -> store.claimRepair(prior, source, List.of(id(1)), "refusal-repair", "trace", 60_000))
                .hasMessage("PROVIDER_REFUSAL");
        assertThat(jdbc.queryForObject("SELECT SUM(provider_calls) FROM novel_translation_job", Integer.class)).isEqualTo(1);
    }

    @Test
    void existingCompatiblePartialCanFillMissingWithoutChangingFirstFailureOrCost() {
        // 준비: 기존 계정의 부분 번역과 다른 계정의 완료본이 정상 문장에서는 일치한다.
        var prior = create(22, "prior-partial");
        var priorOwner = store.claim(prior.cacheKey(), false, 60_000).orElseThrow();
        store.reserveCall(priorOwner);
        store.merge(priorOwner, Map.of(id(0), "고양이"), Map.of(id(1), "TAIL_INTERRUPTED"), 5, 6);
        store.finish(priorOwner, source, false);
        var owner = owner(11, "ready-for-partial", "compatible");
        store.merge(owner, values(), Map.of(), 0, 0);
        store.finish(owner, source);

        // 실행: 이후 같은 정책의 완료 캐시를 연결해 미완료 부분만 채운다.
        var completed = shared.attach("compatible", store.byCache(prior.cacheKey()).orElseThrow(), source);

        // 검증: 정상값과 자기 최초 실패·이전 비용은 그대로 남는다.
        assertThat(completed.state()).isEqualTo("SUCCEEDED");
        assertThat(completed.results()).containsExactlyInAnyOrderEntriesOf(values());
        assertThat(completed.providerCalls()).isEqualTo(1);
        assertThat(completed.inputTokens()).isEqualTo(5);
        assertThat(completed.outputTokens()).isEqualTo(6);
        assertThat(store.initialAttemptState(completed)).isEqualTo("PARTIAL");
        assertThat(jdbc.queryForObject("SELECT errors_json FROM novel_initial_attempt WHERE cache_key=?", String.class,
                prior.cacheKey())).contains("TAIL_INTERRUPTED");
    }

    @Test
    void paidPartialExplicitlyJoinsActiveProducerAndKeepsPollingUntilItsTailArrives() {
        // 준비: 기존 유료 부분 번역의 정상 prefix가 있고 공통 producer는 아직 첫 문장 전이다.
        var prior = paidPartial(22, "paid-join-prior", "고양이");
        var owner = owner(11, "paid-join-producer", "paid-join");
        var original = store.byCache(prior.cacheKey()).orElseThrow();
        assertThat(shared.attach("paid-join", original, source).state()).isEqualTo("PARTIAL");

        // 실행: 사용자의 명시 시작으로 합류한 뒤 공통 prefix와 tail이 순서대로 도착한다.
        var waiting = shared.attach("paid-join", original, source, true);
        assertThat(store.claim(waiting.cacheKey(), true, 60_000)).isEmpty();
        store.merge(owner, Map.of(id(0), "고양이", id(1), "개"), Map.of(), 1, 1);
        var middle = shared.sync(waiting, source);
        store.merge(owner, Map.of(id(2), "새"), Map.of(), 1, 1);
        store.finish(owner, source);
        var completed = shared.attach("paid-join", middle, source);

        // 검증: 기존 호출 이력 때문에 sync가 끊기지 않으며 자기 정상값·비용·최초 실패는 보존된다.
        assertThat(waiting.state()).isEqualTo("RUNNING");
        assertThat(waiting.owner()).isNull();
        assertThat(waiting.results()).containsExactlyEntriesOf(Map.of(id(0), "고양이"));
        assertThat(middle.state()).isEqualTo("RUNNING");
        assertThat(middle.results()).containsKeys(id(0), id(1));
        assertThat(completed.state()).isEqualTo("SUCCEEDED");
        assertThat(completed.results()).containsExactlyInAnyOrderEntriesOf(values());
        assertThat(completed.providerCalls()).isEqualTo(1);
        assertThat(completed.inputTokens()).isEqualTo(5);
        assertThat(completed.outputTokens()).isEqualTo(6);
        assertThat(store.initialAttemptState(completed)).isEqualTo("PARTIAL");
        assertThat(store.merge(prior, Map.of(id(1), "늦은 옛 응답"), Map.of(), 0, 0)).isFalse();
    }

    @Test
    void paidFailedJobExplicitlyJoinsActiveProducerWithoutAnotherProviderReservation() {
        // 준비: 자기 최초 실행은 유료 호출 후 실패했고 다른 계정의 같은 번역이 진행 중이다.
        var failed = create(22, "failed-join-prior");
        var failedOwner = store.claim(failed.cacheKey(), false, 60_000).orElseThrow();
        store.reserveCall(failedOwner);
        store.merge(failedOwner, Map.of(), Map.of(id(0), "PROVIDER_TIMEOUT"), 5, 0);
        store.finish(failedOwner, source, false);
        var owner = owner(11, "failed-join-producer", "failed-join");

        // 실행: 명시 재개로 다른 producer에 합류하고 완성된 첫 문장을 받는다.
        var waiting = shared.attach("failed-join", store.byCache(failed.cacheKey()).orElseThrow(), source, true);
        assertThat(store.claim(waiting.cacheKey(), true, 60_000)).isEmpty();
        store.merge(owner, Map.of(id(0), "고양이"), Map.of(), 0, 0);
        var updated = shared.sync(waiting, source);

        // 검증: FAILED에 머물러 FE polling이 끊기지 않고 새 자기 provider 호출도 없다.
        assertThat(waiting.state()).isEqualTo("RUNNING");
        assertThat(updated.state()).isEqualTo("RUNNING");
        assertThat(updated.results()).containsEntry(id(0), "고양이");
        assertThat(updated.providerCalls()).isEqualTo(1);
        assertThat(store.initialAttemptState(updated)).isEqualTo("FAILED");
    }

    @Test
    void lateSharedConflictPreservesPaidNormalSentenceAndEndsWaiting() {
        // 준비: 옛 정상 prefix는 보존한 채 아직 비어 있는 공통 stream에 명시 합류한다.
        var prior = paidPartial(22, "late-conflict-prior", "보존할 기존 정상 번역");
        var owner = owner(11, "late-conflict-producer", "late-conflict");
        var waiting = shared.attach("late-conflict", store.byCache(prior.cacheKey()).orElseThrow(), source, true);

        // 실행: 공통 stream에서 같은 ID에 다른 정상 문자열이 늦게 도착한다.
        store.merge(owner, Map.of(id(0), "공통 생성의 다른 번역", id(1), "개"), Map.of(), 0, 0);
        var conflicted = shared.sync(waiting, source);

        // 검증: 기존 값을 덮거나 서로 다른 응답을 섞지 않고 대기를 명확히 끝낸다.
        assertThat(waiting.state()).isEqualTo("RUNNING");
        assertThat(conflicted.state()).isEqualTo("PARTIAL");
        assertThat(conflicted.leaseUntil()).isZero();
        assertThat(conflicted.results()).containsExactlyEntriesOf(Map.of(id(0), "보존할 기존 정상 번역"));
        assertThat(conflicted.errors()).containsEntry(id(1), "SHARED_TRANSLATION_CONFLICT")
                .containsEntry(id(2), "SHARED_TRANSLATION_CONFLICT");
        assertThat(conflicted.providerCalls()).isEqualTo(1);
        assertThat(shared.sync(conflicted, source).eventSequence()).isEqualTo(conflicted.eventSequence());
    }

    @Test
    void newAccountReusesTerminalPartialPrefixAndOnlyRepairsTheMissingSentence() {
        // 준비: 공통 A는 중간 문장만 실패했고 뒤쪽 정상 문장까지 이미 저장됐다.
        var owner = owner(11, "partial-cache-producer", "partial-cache");
        store.reserveCall(owner);
        store.merge(owner, Map.of(id(0), "고양이", id(2), "새"), Map.of(id(1), "TAIL"), 0, 0);
        store.finish(owner, source, false);

        // 실행: 새 계정의 일반 시작은 정상 prefix를 재사용하고 실패 문장만 명시 복구한다.
        var follower = shared.attach("partial-cache", create(22, "partial-cache-follower"), source);
        assertThat(store.claim(follower.cacheKey(), false, 60_000)).isEmpty();
        var repair = store.claimRepair(follower, source, List.of(id(0), id(1), id(2)), "partial-cache-repair", "trace", 60_000);
        assertThat(store.reserveRepairCall(repair.repair())).isTrue();
        store.mergeRepair(repair.repair(), Map.of(id(1), "개"), Map.of(), 1, 1);
        store.finishRepair(repair.repair(), source);
        var completed = store.byCache(follower.cacheKey()).orElseThrow();

        // 검증: 다른 계정의 정상값은 유료 0회로 가져오고 복구 호출의 target만 한 문장이다.
        assertThat(follower.state()).isEqualTo("PARTIAL");
        assertThat(follower.results()).containsEntry(id(0), "고양이").containsEntry(id(2), "새");
        assertThat(follower.providerCalls()).isZero();
        assertThat(repair.repair().targetIds()).containsExactly(id(1));
        assertThat(completed.state()).isEqualTo("SUCCEEDED");
        assertThat(completed.providerCalls()).isEqualTo(1);
        assertThat(completed.results()).containsExactlyInAnyOrderEntriesOf(values());
        assertThat(shared.completed("partial-cache")).hasValueSatisfying(job -> assertThat(job.cacheKey()).isEqualTo(completed.cacheKey()));
    }

    @Test
    void ownLiveExecutionCannotBeTakenOverByAnotherActiveProducer() {
        // 준비: 구형 실행으로 자기 provider lease가 이미 살아 있고 별도 공통 실행도 있다.
        var own = create(22, "live-own");
        var ownOwner = store.claim(own.cacheKey(), false, 60_000).orElseThrow();
        var producer = owner(11, "live-other", "live-own-policy");

        // 실행: 자기 작업을 공통 identity에 연결하거나 중복 시작해도 live owner는 바꾸지 않는다.
        var attached = shared.attach("live-own-policy", store.byCache(own.cacheKey()).orElseThrow(), source, true);
        assertThat(store.claim(own.cacheKey(), false, 60_000)).isEmpty();
        var unchanged = shared.sync(attached, source);

        // 검증: 원래 실행의 fence·정상 저장 권한이 유지되고 다른 provider prefix로 덮이지 않는다.
        assertThat(unchanged.owner()).isEqualTo(ownOwner.owner());
        assertThat(store.merge(ownOwner, Map.of(id(0), "자기 실행 정상값"), Map.of(), 0, 0)).isTrue();
        assertThat(store.byCache(producer.cacheKey()).orElseThrow().owner()).isEqualTo(producer.owner());
    }

    @Test
    void ownLiveRepairCannotBeTakenOverByAnotherActiveProducer() {
        // 준비: 자기 선택 복구가 실제 lease를 소유한 상태에서 다른 계정의 A가 시작됐다.
        var prior = paidPartial(22, "live-repair-own", "고양이");
        var repair = store.claimRepair(prior, source, List.of(id(1)), "live-own-repair", "trace", 60_000);
        owner(11, "live-repair-other", "live-repair-policy");

        // 실행: 명시 시작과 조회 모두 자신의 살아 있는 복구 소유권을 보존한다.
        var attached = shared.attach("live-repair-policy", store.byCache(prior.cacheKey()).orElseThrow(), source, true);
        assertThat(store.claim(prior.cacheKey(), true, 60_000)).isEmpty();
        shared.sync(attached, source);

        // 검증: 늦은 공통 결과로 복구 작업을 무효화하지 않는다.
        assertThat(store.mergeRepair(repair.repair(), Map.of(id(1), "개"), Map.of(), 0, 0)).isTrue();
        assertThat(store.byCache(prior.cacheKey()).orElseThrow().results()).containsEntry(id(1), "개");
    }

    @Test
    void malformedOwnResultCannotBePromotedByOtherwiseCompleteProducer() {
        // 준비: 정상적으로 합류한 자기 행에 원문에 없는 ID가 잘못 저장된 상황을 재현한다.
        var owner = owner(11, "invalid-own-producer", "invalid-own");
        var follower = shared.attach("invalid-own", create(22, "invalid-own-follower"), source);
        store.claim(follower.cacheKey(), false, 60_000);
        follower = shared.sync(follower, source);
        jdbc.update("UPDATE novel_translation_job SET result_json=? WHERE cache_key=?",
                "{\"" + id(0) + "\":\"고양이\",\"unknown-id\":\"보존할 불량 원자료\"}", follower.cacheKey());
        store.merge(owner, values(), Map.of(), 0, 0);
        store.finish(owner, source);

        // 실행: provider 완료본만으로 자기 잘못된 행까지 성공시키려 하지 않는다.
        var unchanged = shared.sync(store.byCache(follower.cacheKey()).orElseThrow(), source);

        // 검증: 불량 원자료는 삭제하지 않지만 회차 전체 성공으로 승격하지도 않는다.
        assertThat(unchanged.state()).isNotEqualTo("SUCCEEDED");
        assertThat(unchanged.results()).containsEntry("unknown-id", "보존할 불량 원자료");
        assertThat(unchanged.providerCalls()).isZero();
    }

    @Test
    void conflictingValidSentenceIsPreservedInsteadOfUsingAnotherCompleteResult() {
        // 준비: 기존 계정의 정상 문장은 다른 완료본과 값이 다르다.
        var prior = create(22, "conflict-prior");
        var priorOwner = store.claim(prior.cacheKey(), false, 60_000).orElseThrow();
        store.merge(priorOwner, Map.of(id(0), "원래 보존할 번역"), Map.of(), 0, 0);
        store.finish(priorOwner, source, false);
        var owner = owner(11, "conflict-ready", "conflict");
        store.merge(owner, values(), Map.of(), 0, 0);
        store.finish(owner, source);

        // 실행: 같은 정책의 완료본을 연결하려고 한다.
        var unchanged = shared.attach("conflict", store.byCache(prior.cacheKey()).orElseThrow(), source);

        // 검증: 정상 문장을 덮거나 서로 다른 결과를 강제로 섞지 않는다.
        assertThat(unchanged.state()).isEqualTo("PARTIAL");
        assertThat(unchanged.results()).containsExactlyEntriesOf(Map.of(id(0), "원래 보존할 번역"));
    }

    @Test
    void incompleteOrBlankPersistedSuccessIsNotPublished() {
        // 준비: 잘못 저장된 성공 표식만 있는 행을 만든다.
        var bad = create(11, "invalid-ready");
        jdbc.update("UPDATE novel_translation_job SET state='SUCCEEDED',result_json=? WHERE cache_key=?",
                "{\"" + id(0) + "\":\" \"}", bad.cacheKey());

        // 실행: opaque 과거 행을 공통 완료본으로 등록하려고 한다.
        shared.publish("invalid", store.byCache(bad.cacheKey()).orElseThrow(), source);

        // 검증: 전체 기대 ID·내용 검증을 통과하지 못하면 재사용되지 않는다.
        assertThat(shared.completed("invalid")).isEmpty();
    }

    @Test
    void overlappingRepairsAcrossAccountsAreBlockedWhileOriginalHoleRepairStillWorks() {
        // 준비: 원래 A 실행 중 확인된 hole을 같은 job에서 복구한다.
        var owner = owner(11, "repair-first", "repair");
        store.merge(owner, Map.of(id(0), "고양이"), Map.of(id(1), "SCHEMA_INVALID"), 0, 0);
        var follower = shared.attach("repair", create(22, "repair-second"), source);
        store.claim(follower.cacheKey(), false, 60_000);
        follower = shared.sync(follower, source);
        var repair = store.claimRepair(owner, source, List.of(id(1)), "repair-owner", "trace", 60_000);
        var waiting = follower;

        // 실행·검증: 다른 계정의 같은 target은 유료 실행 전에 차단하고 같은 job 중복은 병합한다.
        assertThat(repair.claimed()).isTrue();
        assertThatThrownBy(() -> store.claimRepair(waiting, source, List.of(id(1)), "repair-follower", "trace", 60_000))
                .hasMessage("REPAIR_SHARED_IN_PROGRESS");
        assertThat(store.claimRepair(owner, source, List.of(id(1)), "repair-owner-duplicate", "trace", 60_000).claimed()).isFalse();
        assertThat(store.mergeRepair(repair.repair(), Map.of(id(1), "개"), Map.of(), 0, 0)).isTrue();
        store.finishRepair(repair.repair(), source);
        store.merge(owner, Map.of(id(2), "새"), Map.of(), 0, 0);
        store.finish(owner, source, false);
        assertThat(shared.attach("repair", store.byCache(waiting.cacheKey()).orElseThrow(), source).state()).isEqualTo("SUCCEEDED");
    }

    @Test
    void liveRepairPreventsAnotherAccountFromStartingWholeEpisodeAfterOriginalLeaseEnds() {
        // 준비: 최초 A는 종료됐지만 실패 부분을 명시 복구하는 provider는 아직 실행 중이다.
        var owner = owner(11, "repair-lease-first", "repair-lease");
        store.merge(owner, Map.of(id(0), "고양이"), Map.of(id(1), "TAIL", id(2), "TAIL"), 0, 0);
        store.finish(owner, source, false);
        var repair = store.claimRepair(owner, source, List.of(id(1), id(2)), "repair-lease", "trace", 60_000);
        var follower = shared.attach("repair-lease", create(22, "repair-lease-second"), source);

        // 실행: 다른 계정은 전체 시작을 눌러도 살아 있는 복구 provider에 합류한다.
        assertThat(store.claim(follower.cacheKey(), false, 60_000)).isEmpty();
        var waiting = shared.sync(follower, source);

        // 검증: 최초 lease가 0이어도 새 전체 번역을 실행하지 않고 복구 lease를 전달한다.
        assertThat(waiting.state()).isEqualTo("RUNNING");
        assertThat(waiting.leaseUntil()).isEqualTo(repair.repair().leaseUntil());
        assertThat(waiting.providerCalls()).isZero();
    }

    @Test
    void followerCannotStartIndependentRepairWhileOriginalProducerIsStillStreaming() {
        // 준비: 공유 A 실행 중 한 문장 실패가 확인됐지만 producer 복구는 아직 시작하지 않았다.
        var owner = owner(11, "active-repair-first", "active-repair");
        store.merge(owner, Map.of(id(0), "고양이"), Map.of(id(1), "SCHEMA_INVALID"), 0, 0);
        var follower = shared.attach("active-repair", create(22, "active-repair-second"), source);
        store.claim(follower.cacheKey(), false, 60_000);
        var waiting = shared.sync(follower, source);

        // 실행·검증: follower는 독립 복구를 만들지 않고 원래 producer의 복구만 허용한다.
        assertThatThrownBy(() -> store.claimRepair(waiting, source, List.of(id(1)), "follower-hole", "trace", 60_000))
                .hasMessage("REPAIR_SHARED_IN_PROGRESS");
        assertThat(store.claimRepair(owner, source, List.of(id(1)), "producer-hole", "trace", 60_000).claimed()).isTrue();
        assertThat(store.byCache(waiting.cacheKey()).orElseThrow().providerCalls()).isZero();
    }

    @Test
    void sourceChangePreventsCopyAndPrivateJobCannotChangeSharedIdentity() {
        // 준비: 최초 원문에서 완료된 공통 번역과 다른 계정의 대기 작업을 둔다.
        var owner = owner(11, "revision-first", "revision");
        store.merge(owner, values(), Map.of(), 0, 0);
        store.finish(owner, source);
        var follower = create(22, "revision-second");
        var changed = new SentenceSegmenter().segment(key, "題", List.of("別の本文。"), null, null);
        store.saveSource(key, changed, System.currentTimeMillis() + 1000);

        // 실행·검증: 낡은 원문의 성공도 새 원문에는 복사하지 않으며 매핑은 불변이다.
        assertThat(shared.attach("revision", follower, source).results()).isEmpty();
        assertThatThrownBy(() -> shared.attach("different-policy", follower, source))
                .hasMessage("SHARED_CACHE_IDENTITY_CONFLICT");
    }

    private NovelRepository.Job create(long actor, String cache) {
        return store.create(key, source, actor, cache, "request-" + cache);
    }

    private NovelRepository.Job owner(long actor, String cache, String sharedKey) {
        var job = shared.attach(sharedKey, create(actor, cache), source);
        return store.claim(job.cacheKey(), false, 60_000).orElseThrow();
    }

    private NovelRepository.Job paidPartial(long actor, String cache, String preserved) {
        var job = create(actor, cache);
        var owner = store.claim(job.cacheKey(), false, 60_000).orElseThrow();
        store.reserveCall(owner);
        store.merge(owner, Map.of(id(0), preserved), Map.of(id(1), "TAIL", id(2), "TAIL"), 5, 6);
        store.finish(owner, source, false);
        return owner;
    }

    private String id(int index) {
        return source.segments().get(index).id();
    }

    private Map<String, String> values() {
        var values = new LinkedHashMap<String, String>();
        values.put(id(0), "고양이");
        values.put(id(1), "개");
        values.put(id(2), "새");
        return values;
    }
}
