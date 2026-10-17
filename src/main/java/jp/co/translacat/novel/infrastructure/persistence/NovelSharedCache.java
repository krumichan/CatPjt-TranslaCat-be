package jp.co.translacat.novel.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.novel.application.NovelRepository;
import jp.co.translacat.novel.application.NovelRepository.Job;
import jp.co.translacat.novel.application.NovelRepository.RepairClaim;
import jp.co.translacat.novel.domain.NovelProblem;
import jp.co.translacat.novel.domain.NovelTargetLanguage;
import jp.co.translacat.novel.domain.SourceEpisode;
import jp.co.translacat.novel.domain.SourceText;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** 계정별 권한·작업 이력을 유지하면서 동일 번역의 저장 결과와 실행만 공유한다. */
public final class NovelSharedCache {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;
    private final NovelRepository store;

    private record Link(String sharedKey, String producer) {}

    public NovelSharedCache(JdbcTemplate jdbc, TransactionTemplate transactions,
                            ObjectMapper json, NovelRepository store) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.json = json;
        this.store = store;
    }

    public Optional<Job> completed(String sharedKey) {
        return producer(sharedKey).flatMap(store::byCache).filter(job -> "SUCCEEDED".equals(job.state()));
    }

    public void publish(String sharedKey, Job candidate, SourceEpisode source) {
        if (!complete(candidate, source)) return;

        // 공통 identity를 먼저 잠근다. 완료된 기존 번역과 계정별 원본 행은 덮어쓰지 않는다.
        transactions.executeWithoutResult(status -> {
            lockShared(sharedKey);
            Job current = lockJob(candidate.cacheKey());
            if (!complete(current, source)) return;
            Job previous = currentProducer(sharedKey).filter(value -> "SUCCEEDED".equals(value.state())).orElse(null);
            if (previous != null && sameSource(previous, current) && complete(previous, source)) return;
            jdbc.update("UPDATE novel_translation_shared SET producer_cache_key=? WHERE shared_key=?",
                    current.cacheKey(), sharedKey);
        });
    }

    public Job attach(String sharedKey, Job job, SourceEpisode source) {
        return attach(sharedKey, job, source, false);
    }

    public Job attach(String sharedKey, Job job, SourceEpisode source, boolean explicitResume) {
        return transactions.execute(status -> {
            // 다른 요청자의 작업 ID로 접근하지 않고 자기 행에만 공유 결과를 반영한다.
            lockShared(sharedKey);
            ensureLink(sharedKey, job.cacheKey());
            Job current = lockJob(job.cacheKey());
            if ("CANCELLED".equals(current.state()) && !explicitResume) return current;
            if (complete(current, source)) {
                publish(sharedKey, current, source);
                return current;
            }
            if (ownsActiveWork(current)) return current;

            // 기존 부분 번역과 공통 완료본이 충돌하면 정상 값을 보존하고 공유하지 않는다.
            Job producer = currentProducer(sharedKey).orElse(null);
            Job ready = producer != null && "SUCCEEDED".equals(producer.state()) ? producer : null;
            if (ready != null && sameSource(current, ready) && complete(ready, source)
                    && compatible(current, ready)) {
                jdbc.update("UPDATE novel_translation_shared_job SET producer_cache_key=? WHERE cache_key=?",
                        ready.cacheKey(), current.cacheKey());
                return copy(current, ready, source, true);
            }
            if (producer != null && refused(producer) && sameSource(current, producer)
                    && compatible(current, producer) && !"CANCELLED".equals(current.state())) {
                // 같은 입력의 provider 거절은 계정 변경으로 새 실행을 만들지 않고 자기 실패 상태로 전달한다.
                jdbc.update("UPDATE novel_translation_shared_job SET producer_cache_key=? WHERE cache_key=?",
                        producer.cacheKey(), current.cacheKey());
                return copy(current, producer, source, false);
            }
            if (producer != null && !producer.cacheKey().equals(current.cacheKey()) && sameSource(current, producer)
                    && !"CANCELLED".equals(current.state())) {
                // 신규 계정도 검증된 정상 prefix를 보존하고 누락된 부분만 명시 복구할 수 있다.
                boolean reusablePartial = "QUEUED".equals(current.state()) && current.providerCalls() == 0
                        && "PARTIAL".equals(producer.state()) && compatible(current, producer);
                if (reusablePartial || explicitResume && active(producer)) {
                    jdbc.update("UPDATE novel_translation_shared_job SET producer_cache_key=? WHERE cache_key=?",
                            producer.cacheKey(), current.cacheKey());
                    return compatibleOverlap(current, producer)
                            ? copy(current, producer, source, false) : conflict(current, source);
                }
            }
            // 취소 후 명시 시작도 유효한 완료 캐시가 있을 때만 이 경로에서 복원한다.
            return "CANCELLED".equals(current.state()) ? current : syncLocked(current, source);
        });
    }

    public Job sync(Job job, SourceEpisode source) {
        Link link = link(job.cacheKey()).orElse(null);
        if (link == null) return job;
        return transactions.execute(status -> {
            lockShared(link.sharedKey());
            return syncLocked(lockJob(job.cacheKey()), source);
        });
    }

    public Optional<Job> claim(String cacheKey, Supplier<Optional<Job>> action) {
        Link link = link(cacheKey).orElse(null);
        if (link == null) return action.get();
        return transactions.execute(status -> {
            // 공통 행의 lock은 DB claim까지만 유지한다. provider 실행은 호출자가 나중에 수행한다.
            lockShared(link.sharedKey());
            Job current = currentProducer(link.sharedKey()).orElse(null);
            if (current != null && refused(current)) throw new NovelProblem("PROVIDER_REFUSAL", 422);
            if (current != null && "SUCCEEDED".equals(current.state())) return Optional.empty();
            if (current != null && !current.cacheKey().equals(cacheKey) && active(current)) {
                if (ownsActiveWork(lockJob(cacheKey))) return Optional.empty();
                jdbc.update("UPDATE novel_translation_shared_job SET producer_cache_key=? WHERE cache_key=?",
                        current.cacheKey(), cacheKey);
                return Optional.empty();
            }

            Optional<Job> claimed = action.get();
            if (claimed.isPresent()) {
                jdbc.update("UPDATE novel_translation_shared SET producer_cache_key=? WHERE shared_key=?",
                        cacheKey, link.sharedKey());
                jdbc.update("UPDATE novel_translation_shared_job SET producer_cache_key=? WHERE cache_key=?",
                        cacheKey, cacheKey);
            }
            return claimed;
        });
    }

    public RepairClaim repair(Job job, List<String> ids, Supplier<RepairClaim> action) {
        Link link = link(job.cacheKey()).orElse(null);
        if (link == null) return action.get();
        return transactions.execute(status -> {
            lockShared(link.sharedKey());
            Job producer = currentProducer(link.sharedKey()).orElse(null);
            if (producer != null && refused(producer)) throw new NovelProblem("PROVIDER_REFUSAL", 422);
            if (producer != null && !producer.cacheKey().equals(job.cacheKey()) && active(producer)) {
                // 공유 원본이 아직 진행 중이면 follower가 독립 유료 복구를 시작해 연결을 끊지 않는다.
                throw new NovelProblem("REPAIR_SHARED_IN_PROGRESS", 409, true, 1000);
            }
            Set<String> requested = new HashSet<>(ids);
            List<String> activeTargets = jdbc.queryForList(
                    "SELECT r.targets_json FROM novel_repair r JOIN novel_translation_shared_job j "
                            + "ON j.cache_key=r.cache_key WHERE j.shared_key=? AND r.cache_key<>? "
                            + "AND r.state='RUNNING' AND r.lease_until>? FOR UPDATE",
                    String.class, link.sharedKey(), job.cacheKey(), System.currentTimeMillis());
            for (String targets : activeTargets) {
                if (readTargets(targets).stream().anyMatch(requested::contains)) {
                    throw new NovelProblem("REPAIR_SHARED_IN_PROGRESS", 409, true, 1000);
                }
            }

            // 같은 job의 원래 A와 확인된 hole 복구는 기존 fence·대상 판정을 그대로 사용한다.
            return action.get();
        });
    }

    public void publishLinked(Job job, SourceEpisode source) {
        link(job.cacheKey()).ifPresent(value -> publish(value.sharedKey(), job, source));
    }

    private Job syncLocked(Job current, SourceEpisode source) {
        if (!Set.of("QUEUED", "RUNNING", "PARTIAL", "FAILED").contains(current.state())
                || ownsActiveWork(current)) return current;
        Link link = link(current.cacheKey()).orElse(null);
        if (link == null || link.producer() == null || link.producer().equals(current.cacheKey())) return current;
        Job producer = lockedJob(link.producer()).orElse(null);
        if (producer == null || !sameSource(current, producer)) return current;
        if (!compatibleOverlap(current, producer)) return conflict(current, source);
        return copy(current, producer, source, false);
    }

    private Job copy(Job current, Job producer, SourceEpisode source, boolean completedReuse) {
        // 원문 버전·ID·완성 문자열을 확인하고 이미 저장된 정상 값은 putIfAbsent로 보존한다.
        Set<String> expected = expected(source);
        if (!current.revision().equals(source.revision()) || !sourceCurrent(current)
                || !expected.containsAll(current.results().keySet())
                || current.results().values().stream().anyMatch(value -> value == null || SourceText.isBlank(value))
                || !expected.containsAll(producer.results().keySet())
                || producer.results().values().stream().anyMatch(value -> value == null || SourceText.isBlank(value))) return current;
        Map<String, String> values = new HashMap<>(current.results());
        producer.results().forEach(values::putIfAbsent);
        Map<String, String> errors = "RUNNING".equals(producer.state()) && active(producer)
                ? new HashMap<>() : new HashMap<>(current.errors());
        producer.errors().forEach((id, code) -> {
            if (expected.contains(id) && !values.containsKey(id)) errors.put(id, code);
        });
        values.keySet().forEach(errors::remove);

        // 부분 prefix의 개수만으로 성공으로 승격하지 않는다. 최종 검증된 producer만 완료를 전달한다.
        String state;
        long lease = 0;
        if (complete(producer, source)) {
            state = "SUCCEEDED";
        } else if ("RUNNING".equals(producer.state()) && active(producer)) {
            state = "RUNNING";
            lease = Math.max(producer.leaseUntil(), activeRepairLease(producer.cacheKey()));
        } else if ("QUEUED".equals(producer.state())) {
            state = "QUEUED";
        } else {
            state = values.isEmpty() ? "FAILED" : "PARTIAL";
            String fallback = "CANCELLED".equals(producer.state()) ? "SHARED_PRODUCER_CANCELLED"
                    : "RUNNING".equals(producer.state()) ? "JOB_LEASE_EXPIRED" : "SHARED_TRANSLATION_INCOMPLETE";
            expected.stream().filter(id -> !values.containsKey(id)).forEach(id -> errors.putIfAbsent(id, fallback));
        }
        if (completedReuse && !"SUCCEEDED".equals(state)) return current;
        boolean changed = !state.equals(current.state()) || !values.equals(current.results())
                || !errors.equals(current.errors());
        if (!changed) {
            if (lease != current.leaseUntil()) {
                jdbc.update("UPDATE novel_translation_job SET lease_until=? WHERE cache_key=?", lease, current.cacheKey());
                return store.byCache(current.cacheKey()).orElseThrow();
            }
            return current;
        }

        // 계정별 과금·trace는 복제하지 않는다. 이 행 자체의 상태 변경만 sequence에 기록한다.
        jdbc.update("UPDATE novel_translation_job SET state=?,result_json=?,errors_json=?,owner_token=NULL,"
                        + "lease_until=?,event_sequence=event_sequence+1,updated_at=? WHERE cache_key=?",
                state, write(values), write(errors), lease, System.currentTimeMillis(), current.cacheKey());
        if (Set.of("SUCCEEDED", "PARTIAL", "FAILED").contains(state)) recordInitial(current.cacheKey(), state, errors);
        return store.byCache(current.cacheKey()).orElseThrow();
    }

    private Job conflict(Job current, SourceEpisode source) {
        if (!source.revision().equals(current.revision()) || !sourceCurrent(current)) return current;
        Map<String, String> errors = new HashMap<>(current.errors());
        expected(source).stream().filter(id -> !current.results().containsKey(id))
                .forEach(id -> errors.put(id, "SHARED_TRANSLATION_CONFLICT"));
        if ("PARTIAL".equals(current.state()) && current.errors().equals(errors) && current.leaseUntil() == 0) return current;

        // 뒤늦게 도착한 공통 결과가 자기 정상 문장과 다르면 기존 값을 보존하고 대기를 종료한다.
        jdbc.update("UPDATE novel_translation_job SET state='PARTIAL',errors_json=?,owner_token=NULL,lease_until=0,"
                        + "event_sequence=event_sequence+1,updated_at=? WHERE cache_key=?",
                write(errors), System.currentTimeMillis(), current.cacheKey());
        recordInitial(current.cacheKey(), "PARTIAL", errors);
        return store.byCache(current.cacheKey()).orElseThrow();
    }

    private boolean complete(Job job, SourceEpisode source) {
        return job != null && "SUCCEEDED".equals(job.state()) && source.revision().equals(job.revision())
                && NovelTargetLanguage.normalize(job.targetLanguage()).equals(job.targetLanguage())
                && sourceCurrent(job) && job.results().keySet().equals(expected(source))
                && job.results().values().stream().noneMatch(value -> value == null || SourceText.isBlank(value));
    }

    private boolean sameSource(Job first, Job second) {
        return first.sourceKey().equals(second.sourceKey()) && first.revision().equals(second.revision())
                && first.targetLanguage().equals(second.targetLanguage());
    }

    private boolean compatible(Job current, Job producer) {
        return current.results().entrySet().stream()
                .allMatch(entry -> Objects.equals(entry.getValue(), producer.results().get(entry.getKey())));
    }

    private boolean compatibleOverlap(Job current, Job producer) {
        return current.results().entrySet().stream().filter(entry -> producer.results().containsKey(entry.getKey()))
                .allMatch(entry -> Objects.equals(entry.getValue(), producer.results().get(entry.getKey())));
    }

    private boolean refused(Job job) {
        return job.errors().values().stream().anyMatch(code -> code != null && code.contains("REFUS"));
    }

    private Set<String> expected(SourceEpisode source) {
        return source.segments().stream().filter(segment -> !SourceText.isBlank(segment.plainJa()))
                .map(SourceEpisode.Segment::id).collect(Collectors.toSet());
    }

    private boolean sourceCurrent(Job job) {
        return jdbc.queryForList("SELECT revision FROM novel_source_current WHERE source_key=? FOR UPDATE", String.class,
                job.sourceKey()).contains(job.revision());
    }

    private boolean active(Job job) {
        return "RUNNING".equals(job.state()) && (job.leaseUntil() > System.currentTimeMillis()
                || activeRepairLease(job.cacheKey()) > System.currentTimeMillis());
    }

    private boolean ownsActiveWork(Job job) {
        return "RUNNING".equals(job.state()) && job.owner() != null && job.leaseUntil() > System.currentTimeMillis()
                || activeRepairLease(job.cacheKey()) > System.currentTimeMillis();
    }

    private long activeRepairLease(String cacheKey) {
        // 최초 실행 lease가 종료된 뒤에도 실제 복구 소유자의 현재 lease를 확인한다.
        return jdbc.queryForList("SELECT lease_until FROM novel_repair WHERE cache_key=? AND state='RUNNING' FOR UPDATE",
                Long.class, cacheKey).stream().mapToLong(Long::longValue).max().orElse(0);
    }

    private Optional<Link> link(String cacheKey) {
        return jdbc.query("SELECT shared_key,producer_cache_key FROM novel_translation_shared_job WHERE cache_key=?",
                (rs, row) -> new Link(rs.getString(1), rs.getString(2)), cacheKey).stream().findFirst();
    }

    private Optional<String> producer(String sharedKey) {
        return jdbc.query("SELECT producer_cache_key FROM novel_translation_shared WHERE shared_key=?",
                (rs, row) -> rs.getString(1), sharedKey).stream().filter(Objects::nonNull).findFirst();
    }

    private Optional<Job> currentProducer(String sharedKey) {
        return jdbc.query("SELECT producer_cache_key FROM novel_translation_shared WHERE shared_key=? FOR UPDATE",
                (rs, row) -> rs.getString(1), sharedKey).stream().filter(Objects::nonNull).findFirst()
                .flatMap(this::lockedJob);
    }

    private void lockShared(String sharedKey) {
        try {
            jdbc.update("INSERT INTO novel_translation_shared(shared_key,producer_cache_key) VALUES(?,NULL)", sharedKey);
        } catch (DuplicateKeyException ignored) {
            // 동일 identity를 먼저 등록한 요청이 있으면 같은 DB 행을 잠근다.
        }
        jdbc.queryForObject("SELECT shared_key FROM novel_translation_shared WHERE shared_key=? FOR UPDATE",
                String.class, sharedKey);
    }

    private void ensureLink(String sharedKey, String cacheKey) {
        try {
            jdbc.update("INSERT INTO novel_translation_shared_job(cache_key,shared_key,producer_cache_key) VALUES(?,?,NULL)",
                    cacheKey, sharedKey);
        } catch (DuplicateKeyException ignored) {
            if (!link(cacheKey).orElseThrow().sharedKey().equals(sharedKey)) {
                throw new NovelProblem("SHARED_CACHE_IDENTITY_CONFLICT", 409);
            }
        }
    }

    private Job lockJob(String cacheKey) {
        return lockedJob(cacheKey).orElseThrow();
    }

    private Optional<Job> lockedJob(String cacheKey) {
        // MySQL repeatable-read에서도 잠근 현재 행을 사용한다. 뒤이은 일반 조회의 낡은 snapshot으로 대체하지 않는다.
        return jdbc.query("SELECT * FROM novel_translation_job WHERE cache_key=? FOR UPDATE", (rs, row) -> new Job(
                rs.getString("cache_key"), rs.getString("job_id"), rs.getString("source_key"),
                rs.getString("revision"), rs.getLong("actor_id"), rs.getString("state"),
                readMap(rs.getString("result_json")), readMap(rs.getString("errors_json")), rs.getString("owner_token"),
                rs.getLong("fence"), rs.getLong("lease_until"), rs.getLong("event_sequence"),
                rs.getInt("provider_calls"), rs.getLong("input_tokens"), rs.getLong("output_tokens"),
                rs.getString("target_language")), cacheKey).stream().findFirst();
    }

    private void recordInitial(String cacheKey, String state, Map<String, String> errors) {
        try {
            jdbc.update("INSERT INTO novel_initial_attempt(cache_key,state,errors_json,completed_at) VALUES(?,?,?,?)",
                    cacheKey, state, write(errors), System.currentTimeMillis());
        } catch (DuplicateKeyException ignored) {
            // 자기 최초 실패 이력도 이후 공유 완료 결과로 변경하지 않는다.
        }
    }

    private List<String> readTargets(String value) {
        try {
            return json.readValue(value, new TypeReference<>() {});
        } catch (Exception invalid) {
            throw new NovelProblem("PERSISTED_DATA_INVALID", 500);
        }
    }

    private Map<String, String> readMap(String value) {
        try {
            return json.readValue(value, new TypeReference<>() {});
        } catch (Exception invalid) {
            throw new NovelProblem("PERSISTED_DATA_INVALID", 500);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception invalid) {
            throw new NovelProblem("PERSISTENCE_ENCODING_FAILED", 500);
        }
    }
}
