package jp.co.translacat.novel.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.novel.application.NovelRepository;
import jp.co.translacat.novel.domain.EpisodeKey;
import jp.co.translacat.novel.domain.NovelProblem;
import jp.co.translacat.novel.domain.SourceEpisode;
import jp.co.translacat.novel.domain.SentenceSegmenter;
import jp.co.translacat.novel.domain.TranslationPolicy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class NovelStore implements NovelRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;

    public NovelStore(JdbcTemplate jdbc, org.springframework.transaction.PlatformTransactionManager manager,
                      ObjectMapper json) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
        this.json = json;
    }

    public Optional<StoredSource> current(EpisodeKey key) {
        return jdbc.query("SELECT COALESCE(c.source_json,r.source_json),c.fetched_at FROM novel_source_current c "
                        + "JOIN novel_source_revision r ON r.source_key=c.source_key AND r.revision=c.revision "
                        + "WHERE c.source_key=?", (rs, row) -> new StoredSource(
                        read(rs.getString(1), SourceEpisode.class), rs.getLong(2)), key.value()).stream().findFirst();
    }

    public SourceEpisode revision(EpisodeKey key, String revision) {
        List<SourceEpisode> found = jdbc.query("SELECT source_json FROM novel_source_revision WHERE source_key=? AND revision=?",
                (rs, row) -> read(rs.getString(1), SourceEpisode.class), key.value(), revision);
        if (found.isEmpty()) {
            throw new NovelProblem("SOURCE_REVISION_NOT_FOUND", 404);
        }
        return found.getFirst();
    }

    public SourceEpisode saveSource(EpisodeKey key, SourceEpisode source, long fetchedAt) {
        // immutable revision을 먼저 저장한다. 기존 원문/번역 행은 삭제하지 않는다.
        try {
            jdbc.update("INSERT INTO novel_source_revision(source_key,revision,source_json,fetched_at) VALUES(?,?,?,?)",
                    key.value(), source.revision(), write(source), fetchedAt);
        } catch (DuplicateKeyException ignored) {
            // 같은 revision 재조회는 원래 snapshot을 보존한다.
        }
        try {
            jdbc.update("INSERT INTO novel_source_current(source_key,revision,fetched_at,source_json) VALUES(?,?,?,?)",
                    key.value(), source.revision(), fetchedAt, write(source));
        } catch (DuplicateKeyException ignored) {
            // 먼저 시작한 느린 수집 결과가 나중 snapshot을 덮어쓰지 않는다.
            jdbc.update("UPDATE novel_source_current SET revision=?,fetched_at=?,source_json=? WHERE source_key=? AND fetched_at<?",
                    source.revision(), fetchedAt, write(source), key.value(), fetchedAt);
        }
        return current(key).orElseThrow().source();
    }

    public Job create(EpisodeKey key, SourceEpisode source, long actorId, String cacheKey, String requestKey) {
        if (requestKey == null || !requestKey.matches("[A-Za-z0-9._:-]{1,100}")) {
            throw new NovelProblem("IDEMPOTENCY_KEY_INVALID", 400);
        }
        // 사용자+멱등 키는 DB unique constraint로 경쟁 요청에서도 한 cache identity에만 묶인다.
        try {
            jdbc.update("INSERT INTO novel_idempotency(actor_id,request_key,cache_key) VALUES(?,?,?)",
                    actorId, requestKey, cacheKey);
        } catch (DuplicateKeyException ignored) {
            String previous = jdbc.queryForObject("SELECT cache_key FROM novel_idempotency WHERE actor_id=? AND request_key=?",
                    String.class, actorId, requestKey);
            if (!cacheKey.equals(previous)) {
                throw new NovelProblem("IDEMPOTENCY_CONFLICT", 409);
            }
        }
        try {
            jdbc.update("INSERT INTO novel_translation_job(cache_key,job_id,source_key,revision,actor_id,state,"
                            + "result_json,errors_json,owner_token,fence,lease_until,event_sequence,provider_calls,input_tokens,output_tokens,updated_at) "
                            + "VALUES(?,?,?,?,?,'QUEUED','{}','{}',NULL,0,0,0,0,0,0,?)",
                    cacheKey, UUID.randomUUID().toString(), key.value(), source.revision(), actorId, System.currentTimeMillis());
        } catch (DuplicateKeyException ignored) {
            // 원문+정책+actor cache unique는 서로 다른 멱등 키도 동일 작업으로 합친다.
        }
        return byCache(cacheKey).orElseThrow();
    }

    public Optional<Job> byCache(String cacheKey) {
        return jdbc.query("SELECT * FROM novel_translation_job WHERE cache_key=?", mapper(), cacheKey).stream().findFirst();
    }

    public Job byId(EpisodeKey key, String id, long actorId) {
        List<Job> jobs = jdbc.query("SELECT * FROM novel_translation_job WHERE job_id=? AND source_key=? AND actor_id=?",
                mapper(), id, key.value(), actorId);
        if (jobs.isEmpty()) {
            throw new NovelProblem("JOB_NOT_FOUND", 404);
        }
        return jobs.getFirst();
    }

    public Optional<Job> claim(String cacheKey, boolean retry, long leaseMillis) {
        long now = System.currentTimeMillis();
        String owner = UUID.randomUUID().toString();
        // 모든 인스턴스가 같은 DB predicate로 lease를 얻는다. 외부 호출 중 DB lock은 없다.
        // 인계된 회차는 취소 뒤에도 명시 복구로만 재개한다. 원래 A가 다시 생성할 수 없다.
        int changed = jdbc.update("UPDATE novel_translation_job SET state='RUNNING',owner_token=?,fence=fence+1,"
                        + "lease_until=?,event_sequence=event_sequence+1,updated_at=? WHERE cache_key=? AND "
                        + "(state='QUEUED' OR (state='RUNNING' AND lease_until<?) OR (?=TRUE AND state IN ('FAILED','PARTIAL','CANCELLED'))) "
                        + "AND provider_calls<256 AND NOT EXISTS(SELECT 1 FROM novel_repair_target r "
                        + "WHERE r.cache_key=novel_translation_job.cache_key)",
                owner, now + leaseMillis, now, cacheKey, now, retry);
        return changed == 1 ? byCache(cacheKey) : Optional.empty();
    }

    public boolean reserveCall(Job job) {
        return jdbc.update("UPDATE novel_translation_job SET provider_calls=provider_calls+1 WHERE cache_key=? "
                        + "AND owner_token=? AND fence=? AND state='RUNNING' AND lease_until>? AND provider_calls<256 "
                        + "AND EXISTS(SELECT 1 FROM novel_source_current s WHERE s.source_key=novel_translation_job.source_key "
                        + "AND s.revision=novel_translation_job.revision)",
                job.cacheKey(), job.owner(), job.fence(), System.currentTimeMillis()) == 1;
    }

    public boolean merge(Job owner, Map<String, String> results, Map<String, String> errors,
                         long inputTokens, long outputTokens) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            Job current = jdbc.queryForObject("SELECT * FROM novel_translation_job WHERE cache_key=? FOR UPDATE",
                    mapper(), owner.cacheKey());
            if (!owns(current, owner) || current.leaseUntil() <= System.currentTimeMillis()) {
                return false;
            }
            List<String> revisions = jdbc.queryForList("SELECT revision FROM novel_source_current WHERE source_key=?",
                    String.class, owner.sourceKey());
            if (revisions.isEmpty() || !owner.revision().equals(revisions.getFirst())) {
                jdbc.update("UPDATE novel_translation_job SET state='CANCELLED',event_sequence=event_sequence+1 WHERE cache_key=?",
                        owner.cacheKey());
                return false;
            }

            // 청크 전체를 검증한 후 한 짧은 transaction에서 결과와 오류를 함께 반영한다.
            Map<String, String> merged = new HashMap<>(current.results());
            Map<String, String> failed = new HashMap<>(current.errors());
            if (!errors.isEmpty()) recordInitialErrors(current, errors);
            // 복구 대상으로 인계된 ID와 이미 확정된 정상 값은 원래 A의 늦은 응답이 덮지 못한다.
            var handedOff = jdbc.queryForList("SELECT segment_id FROM novel_repair_target WHERE cache_key=?", String.class, owner.cacheKey());
            results.forEach((id, value) -> { if (!handedOff.contains(id)) merged.putIfAbsent(id, value); });
            merged.keySet().forEach(failed::remove);
            errors.forEach((id, code) -> {
                if (!merged.containsKey(id)) {
                    failed.put(id, code);
                }
            });
            jdbc.update("UPDATE novel_translation_job SET result_json=?,errors_json=?,event_sequence=event_sequence+1,"
                            + "input_tokens=input_tokens+?,output_tokens=output_tokens+?,updated_at=? WHERE cache_key=?",
                    write(merged), write(failed), inputTokens, outputTokens, System.currentTimeMillis(), owner.cacheKey());
            return true;
        }));
    }

    public void finish(Job owner, SourceEpisode source) {
        finish(owner, source, true);
    }

    @Override
    public void finish(Job owner, SourceEpisode source, boolean providerVerified) {
        transactions.executeWithoutResult(status -> {
            Job current = jdbc.queryForObject("SELECT * FROM novel_translation_job WHERE cache_key=? FOR UPDATE",
                    mapper(), owner.cacheKey());
            if (!owns(current, owner)) {
                return;
            }
            long target = source.segments().stream().filter(s -> !jp.co.translacat.novel.domain.SourceText.isBlank(s.plainJa())).count();
            String initial = providerVerified && current.results().size() == target ? "SUCCEEDED"
                    : current.results().isEmpty() ? "FAILED" : "PARTIAL";
            Map<String,String> errors = new HashMap<>(current.errors());
            source.segments().stream().filter(s -> !jp.co.translacat.novel.domain.SourceText.isBlank(s.plainJa()) && !current.results().containsKey(s.id()))
                    .forEach(s -> errors.putIfAbsent(s.id(), "PROGRESSIVE_TAIL_INCOMPLETE"));
            // 초기 실행의 실패 이력은 복구 후에도 변경하지 않는다.
            try { jdbc.update("INSERT INTO novel_initial_attempt(cache_key,state,errors_json,completed_at) VALUES(?,?,?,?)",
                    owner.cacheKey(), initial, write(errors), System.currentTimeMillis()); }
            catch (DuplicateKeyException ignored) {
                recordInitialErrors(current, errors);
                jdbc.update("UPDATE novel_initial_attempt SET state=?,completed_at=? WHERE cache_key=? AND completed_at=0", initial, System.currentTimeMillis(), current.cacheKey());
            }
            boolean recovered = jdbc.queryForObject("SELECT COUNT(*) FROM novel_repair WHERE cache_key=? AND state='SUCCEEDED'", Integer.class, owner.cacheKey()) > 0;
            boolean repairing = jdbc.queryForObject("SELECT COUNT(*) FROM novel_repair WHERE cache_key=? AND state='RUNNING'", Integer.class, owner.cacheKey()) > 0;
            String state = repairing ? "RUNNING" : (providerVerified || recovered) && current.results().size() == target ? "SUCCEEDED" : initial;
            jdbc.update("UPDATE novel_translation_job SET state=?,lease_until=0,event_sequence=event_sequence+1,updated_at=? WHERE cache_key=?",
                    state, System.currentTimeMillis(), owner.cacheKey());
            jdbc.update("UPDATE novel_translation_job SET errors_json=? WHERE cache_key=?", write(errors), owner.cacheKey());
        });
    }

    public void releaseRejected(Job owner) {
        jdbc.update("UPDATE novel_translation_job SET state='QUEUED',lease_until=0,owner_token=NULL WHERE cache_key=? AND owner_token=? AND fence=?",
                owner.cacheKey(), owner.owner(), owner.fence());
    }

    public Job expireAbandoned(Job job, SourceEpisode source) {
        expireRepairs(job, source);
        if (job.leaseUntil() == 0 && jdbc.queryForObject("SELECT COUNT(*) FROM novel_initial_attempt WHERE cache_key=? AND completed_at>0", Integer.class, job.cacheKey()) > 0)
            return byCache(job.cacheKey()).orElseThrow();
        if (!"RUNNING".equals(job.state()) || job.leaseUntil() >= System.currentTimeMillis()) {
            return job;
        }
        return transactions.execute(status -> {
            Job current = jdbc.queryForObject("SELECT * FROM novel_translation_job WHERE cache_key=? FOR UPDATE", mapper(), job.cacheKey());
            if (!"RUNNING".equals(current.state()) || current.leaseUntil() >= System.currentTimeMillis()) {
                return current;
            }
            // 프로세스 유실 후 status 조회도 명시적으로 실패를 확정한다. 오래된 owner는 fencing으로 무효화한다.
            Map<String, String> errors = new HashMap<>(current.errors());
            source.segments().stream().filter(segment -> !jp.co.translacat.novel.domain.SourceText.isBlank(segment.plainJa()) && !current.results().containsKey(segment.id()))
                    .forEach(segment -> errors.putIfAbsent(segment.id(), "JOB_LEASE_EXPIRED"));
            String state = current.results().isEmpty() ? "FAILED" : "PARTIAL";
            recordInitialErrors(current, errors);
            jdbc.update("UPDATE novel_initial_attempt SET state=?,completed_at=? WHERE cache_key=? AND completed_at=0", state, System.currentTimeMillis(), job.cacheKey());
            jdbc.update("UPDATE novel_translation_job SET state=?,errors_json=?,fence=fence+1,owner_token=NULL,"
                            + "event_sequence=event_sequence+1 WHERE cache_key=?", state, write(errors), job.cacheKey());
            return byCache(job.cacheKey()).orElseThrow();
        });
    }

    public Glossary glossary(EpisodeKey key, long actorId) {
        return jdbc.query("SELECT glossary_version,terms_json FROM novel_glossary WHERE work_key=? AND actor_id=?",
                (rs, row) -> new Glossary(rs.getString(1), map(rs.getString(2))), workKey(key), actorId)
                .stream().findFirst().orElse(new Glossary(TranslationPolicy.GLOSSARY_VERSION, Map.of()));
    }

    public Glossary saveGlossary(EpisodeKey key, long actorId, String expectedVersion, Map<String, String> terms) {
        if (terms == null || terms.size() > 100 || terms.entrySet().stream().anyMatch(entry -> entry.getKey() == null
                || entry.getValue() == null || entry.getKey().isBlank() || entry.getValue().isBlank()
                || entry.getKey().length() > 128 || entry.getValue().length() > 128)
                || terms.entrySet().stream().mapToInt(entry -> entry.getKey().length() + entry.getValue().length()).sum() > 4000) {
            throw new NovelProblem("GLOSSARY_INVALID", 422);
        }
        String encoded = write(new java.util.TreeMap<>(terms));
        if (encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 6000) {
            throw new NovelProblem("GLOSSARY_TOO_LARGE", 413);
        }
        String version = terms.isEmpty() ? TranslationPolicy.GLOSSARY_VERSION
                : SentenceSegmenter.hash("glossary-context-v1:" + encoded);
        return transactions.execute(status -> {
            List<Glossary> found = jdbc.query("SELECT glossary_version,terms_json FROM novel_glossary "
                            + "WHERE work_key=? AND actor_id=? FOR UPDATE",
                    (rs, row) -> new Glossary(rs.getString(1), map(rs.getString(2))), workKey(key), actorId);
            String current = found.isEmpty() ? TranslationPolicy.GLOSSARY_VERSION : found.getFirst().version();
            if (!current.equals(expectedVersion)) {
                throw new NovelProblem("GLOSSARY_VERSION_CHANGED", 409);
            }
            if (found.isEmpty()) {
                try {
                    jdbc.update("INSERT INTO novel_glossary(work_key,actor_id,glossary_version,terms_json) VALUES(?,?,?,?)",
                            workKey(key), actorId, version, encoded);
                } catch (DuplicateKeyException conflict) {
                    throw new NovelProblem("GLOSSARY_VERSION_CHANGED", 409);
                }
            } else {
                jdbc.update("UPDATE novel_glossary SET glossary_version=?,terms_json=? WHERE work_key=? AND actor_id=?",
                        version, encoded, workKey(key), actorId);
            }
            return new Glossary(version, Map.copyOf(terms));
        });
    }

    private String workKey(EpisodeKey key) { return key.platform() + ":" + key.novel(); }

    public Map<String, Object> diagnostics(Job job) {
        String encoded = jdbc.queryForObject("SELECT diagnostics_json FROM novel_translation_job WHERE cache_key=?", String.class, job.cacheKey());
        try { return json.readValue(encoded, new TypeReference<>() {}); }
        catch (Exception exception) { throw new NovelProblem("PERSISTED_DATA_INVALID", 500); }
    }

    public boolean saveDiagnostics(Job owner, Map<String, Object> diagnostics) {
        // 동일 owner의 계측 snapshot만 저장한다. 재시작 전 늦은 스레드는 새 실행 계측을 덮지 못한다.
        return jdbc.update("UPDATE novel_translation_job SET diagnostics_json=? WHERE cache_key=? AND owner_token=? AND fence=?",
                write(diagnostics), owner.cacheKey(), owner.owner(), owner.fence()) == 1;
    }

    public Job cancel(EpisodeKey key, String jobId, long actorId, SourceEpisode source) {
        return transactions.execute(status -> {
            List<Job> found = jdbc.query("SELECT * FROM novel_translation_job WHERE job_id=? AND source_key=? AND actor_id=? FOR UPDATE",
                    mapper(), jobId, key.value(), actorId);
            if (found.isEmpty()) throw new NovelProblem("JOB_NOT_FOUND", 404);
            Job current = found.getFirst();
            if (!List.of("QUEUED", "RUNNING").contains(current.state())) return current;
            jdbc.update("UPDATE novel_repair SET state='CANCELLED',owner_token=NULL,fence=fence+1,lease_until=0 WHERE cache_key=? AND state='RUNNING'", current.cacheKey());
            // 문장이 모두 보였더라도 provider 최종 검증·저장 확정 전에는 성공이 아니다.
            Map<String, String> errors = new HashMap<>(current.errors());
            source.segments().stream().filter(s -> !jp.co.translacat.novel.domain.SourceText.isBlank(s.plainJa()) && !current.results().containsKey(s.id()))
                    .forEach(s -> errors.putIfAbsent(s.id(), "JOB_CANCELLED"));
            recordInitialErrors(current, errors);
            jdbc.update("UPDATE novel_initial_attempt SET state='CANCELLED',completed_at=? WHERE cache_key=? AND completed_at=0", System.currentTimeMillis(), current.cacheKey());
            jdbc.update("UPDATE novel_translation_job SET state='CANCELLED',errors_json=?,owner_token=NULL,fence=fence+1,"
                            + "lease_until=0,event_sequence=event_sequence+1,updated_at=? WHERE cache_key=?",
                    write(errors), System.currentTimeMillis(), current.cacheKey());
            return byCache(current.cacheKey()).orElseThrow();
        });
    }

    public RepairClaim claimRepair(Job job, SourceEpisode source, List<String> requested, String requestKey,
                                   String traceId, long leaseMillis) {
        if (requestKey == null || !requestKey.matches("[A-Za-z0-9._:-]{1,100}")) throw new NovelProblem("IDEMPOTENCY_KEY_INVALID", 400);
        return transactions.execute(status -> {
            Job current = jdbc.queryForObject("SELECT * FROM novel_translation_job WHERE cache_key=? FOR UPDATE", mapper(), job.cacheKey());
            if (!sourceCurrent(current)) throw new NovelProblem("SOURCE_REVISION_CHANGED", 409);
            var previous = jdbc.query("SELECT * FROM novel_repair WHERE actor_id=? AND request_key=?", repairMapper(), current.actorId(), requestKey);
            if (!previous.isEmpty()) {
                if (!previous.getFirst().cacheKey().equals(current.cacheKey())) throw new NovelProblem("IDEMPOTENCY_CONFLICT", 409);
                return new RepairClaim(previous.getFirst(), false);
            }
            // 한 회차 안에서는 한 복구 작업만 활성화한다. 원래 A lease와는 독립적이다.
            var active = jdbc.query("SELECT * FROM novel_repair WHERE cache_key=? AND state='RUNNING' AND lease_until>?", repairMapper(), current.cacheKey(), System.currentTimeMillis());
            if (!active.isEmpty()) return new RepairClaim(active.getFirst(), false);
            var valid = source.segments().stream().filter(s -> !jp.co.translacat.novel.domain.SourceText.isBlank(s.plainJa())).map(SourceEpisode.Segment::id).toList();
            var targets = new java.util.ArrayList<String>();
            for (String id : requested) {
                if (!valid.contains(id)) throw new NovelProblem("SEGMENT_NOT_FOUND", 404);
                if (current.results().containsKey(id)) continue;
                if (!current.errors().containsKey(id)) throw new NovelProblem("SEGMENT_STILL_PENDING", 409);
                if (current.errors().get(id).contains("REFUS")) throw new NovelProblem("PROVIDER_REFUSAL", 422);
                if (!targets.contains(id)) targets.add(id);
            }
            if (targets.isEmpty()) return new RepairClaim(null, false);
            targets.sort(java.util.Comparator.comparingInt(valid::indexOf));
            String id = UUID.randomUUID().toString(), token = UUID.randomUUID().toString();
            long now = System.currentTimeMillis();
            jdbc.update("INSERT INTO novel_repair(repair_id,cache_key,actor_id,request_key,state,targets_json,results_json,errors_json,owner_token,fence,lease_until,trace_id,created_at) VALUES(?,?,?,?,'RUNNING',?,'{}','{}',?,1,?,?,?)",
                    id, current.cacheKey(), current.actorId(), requestKey, write(targets), token, now + leaseMillis, traceId, now);
            for (String target : targets) {
                int changed = jdbc.update("UPDATE novel_repair_target SET repair_id=? WHERE cache_key=? AND segment_id=?", id, current.cacheKey(), target);
                if (changed == 0) jdbc.update("INSERT INTO novel_repair_target(cache_key,segment_id,repair_id) VALUES(?,?,?)", current.cacheKey(), target, id);
            }
            jdbc.update("UPDATE novel_translation_job SET state='RUNNING',event_sequence=event_sequence+1 WHERE cache_key=?", current.cacheKey());
            return new RepairClaim(repairById(id), true);
        });
    }

    public Optional<Repair> latestRepair(Job job) {
        return jdbc.query("SELECT * FROM novel_repair WHERE cache_key=? ORDER BY created_at DESC,repair_id DESC", repairMapper(), job.cacheKey()).stream().findFirst();
    }

    public String initialAttemptState(Job job) {
        return jdbc.queryForList("SELECT state FROM novel_initial_attempt WHERE cache_key=?", String.class, job.cacheKey())
                .stream().findFirst().orElse(job.leaseUntil() > System.currentTimeMillis() ? "RUNNING" : job.state());
    }

    public Map<String,Object> repairDiagnostics(Repair repair) {
        return jdbc.query("SELECT diagnostics_json FROM novel_repair_diagnostics WHERE repair_id=?", (rs, row) -> {
            try { return json.<Map<String,Object>>readValue(rs.getString(1), new TypeReference<>() {}); }
            catch (Exception invalid) { throw new NovelProblem("PERSISTED_DATA_INVALID", 500); }
        }, repair.id()).stream().findFirst().orElse(Map.of());
    }
    public void saveRepairDiagnostics(Repair owner, Map<String,Object> diagnostics) {
        transactions.executeWithoutResult(status -> {
            Repair current = jdbc.queryForObject("SELECT * FROM novel_repair WHERE repair_id=? FOR UPDATE", repairMapper(), owner.id());
            if (current.fence() != owner.fence() || !java.util.Objects.equals(current.owner(), owner.owner())) return;
            String encoded = write(diagnostics);
            int changed = jdbc.update("UPDATE novel_repair_diagnostics SET diagnostics_json=? WHERE repair_id=?", encoded, owner.id());
            if (changed == 0) jdbc.update("INSERT INTO novel_repair_diagnostics(repair_id,diagnostics_json) VALUES(?,?)", owner.id(), encoded);
        });
    }

    public boolean reserveRepairCall(Repair owner) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            Job job = jdbc.queryForObject("SELECT * FROM novel_translation_job WHERE cache_key=? FOR UPDATE", mapper(), owner.cacheKey());
            if (!repairOwns(repairById(owner.id()), owner) || !sourceCurrent(job) || job.state().equals("CANCELLED") || job.providerCalls() >= 256) return false;
            jdbc.update("UPDATE novel_translation_job SET provider_calls=provider_calls+1 WHERE cache_key=?", owner.cacheKey());
            return true;
        }));
    }

    public boolean mergeRepair(Repair owner, Map<String,String> values, Map<String,String> errors, long input, long output) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            Job job = jdbc.queryForObject("SELECT * FROM novel_translation_job WHERE cache_key=? FOR UPDATE", mapper(), owner.cacheKey());
            Repair current = repairById(owner.id());
            if (!repairOwns(current, owner) || !sourceCurrent(job) || job.state().equals("CANCELLED")) return false;
            if (!current.targetIds().containsAll(values.keySet()) || !current.targetIds().containsAll(errors.keySet())) throw new NovelProblem("REPAIR_TARGET_MISMATCH", 500);
            var merged = new HashMap<>(job.results()); var failed = new HashMap<>(job.errors());
            var accepted = new HashMap<>(current.results()); var rejected = new HashMap<>(current.errors());
            for (var entry : values.entrySet()) {
                String targetOwner = jdbc.queryForObject("SELECT repair_id FROM novel_repair_target WHERE cache_key=? AND segment_id=?", String.class, owner.cacheKey(), entry.getKey());
                if (!owner.id().equals(targetOwner)) return false;
                merged.putIfAbsent(entry.getKey(), entry.getValue()); accepted.put(entry.getKey(), merged.get(entry.getKey()));
                failed.remove(entry.getKey()); rejected.remove(entry.getKey());
            }
            errors.forEach((id, code) -> { if (!merged.containsKey(id)) { failed.put(id, code); rejected.put(id, code); } });
            jdbc.update("UPDATE novel_translation_job SET result_json=?,errors_json=?,input_tokens=input_tokens+?,output_tokens=output_tokens+?,event_sequence=event_sequence+1 WHERE cache_key=?",
                    write(merged), write(failed), input, output, owner.cacheKey());
            jdbc.update("UPDATE novel_repair SET results_json=?,errors_json=? WHERE repair_id=?", write(accepted), write(rejected), owner.id());
            return true;
        }));
    }

    public void finishRepair(Repair owner, SourceEpisode source) {
        transactions.executeWithoutResult(status -> {
            Job job = jdbc.queryForObject("SELECT * FROM novel_translation_job WHERE cache_key=? FOR UPDATE", mapper(), owner.cacheKey());
            Repair current = repairById(owner.id());
            if (!repairOwns(current, owner)) return;
            String state = current.results().size() == current.targetIds().size() ? "SUCCEEDED" : current.results().isEmpty() ? "FAILED" : "PARTIAL";
            jdbc.update("UPDATE novel_repair SET state=?,lease_until=0 WHERE repair_id=?", state, owner.id());
            refreshAggregate(job, source);
        });
    }

    private void expireRepairs(Job job, SourceEpisode source) {
        transactions.executeWithoutResult(status -> {
            Job current = jdbc.queryForObject("SELECT * FROM novel_translation_job WHERE cache_key=? FOR UPDATE", mapper(), job.cacheKey());
            int count = jdbc.update("UPDATE novel_repair SET state='FAILED',owner_token=NULL,fence=fence+1,lease_until=0 WHERE cache_key=? AND state='RUNNING' AND lease_until<?", job.cacheKey(), System.currentTimeMillis());
            if (count > 0) refreshAggregate(current, source);
        });
    }

    private void refreshAggregate(Job job, SourceEpisode source) {
        if (job.state().equals("CANCELLED")) return;
        boolean originalActive = job.leaseUntil() > System.currentTimeMillis();
        boolean repairActive = jdbc.queryForObject("SELECT COUNT(*) FROM novel_repair WHERE cache_key=? AND state='RUNNING'", Integer.class, job.cacheKey()) > 0;
        long count = source.segments().stream().filter(s -> !jp.co.translacat.novel.domain.SourceText.isBlank(s.plainJa())).count();
        String state = originalActive || repairActive ? "RUNNING" : job.results().size() == count ? "SUCCEEDED" : job.results().isEmpty() ? "FAILED" : "PARTIAL";
        jdbc.update("UPDATE novel_translation_job SET state=?,event_sequence=event_sequence+1 WHERE cache_key=?", state, job.cacheKey());
    }

    private boolean sourceCurrent(Job job) {
        return jdbc.queryForList("SELECT revision FROM novel_source_current WHERE source_key=?", String.class, job.sourceKey()).contains(job.revision());
    }
    private void recordInitialErrors(Job job, Map<String,String> errors) {
        var found = jdbc.queryForList("SELECT errors_json FROM novel_initial_attempt WHERE cache_key=?", String.class, job.cacheKey());
        if (found.isEmpty()) {
            jdbc.update("INSERT INTO novel_initial_attempt(cache_key,state,errors_json,completed_at) VALUES(?,'RUNNING',?,0)", job.cacheKey(), write(errors));
        } else {
            var recorded = new HashMap<>(map(found.getFirst())); errors.forEach(recorded::putIfAbsent);
            jdbc.update("UPDATE novel_initial_attempt SET errors_json=? WHERE cache_key=? AND completed_at=0", write(recorded), job.cacheKey());
        }
    }
    private boolean repairOwns(Repair current, Repair owner) {
        return current.state().equals("RUNNING") && current.fence() == owner.fence() && java.util.Objects.equals(current.owner(), owner.owner()) && current.leaseUntil() > System.currentTimeMillis();
    }
    private Repair repairById(String id) { return jdbc.queryForObject("SELECT * FROM novel_repair WHERE repair_id=?", repairMapper(), id); }
    private RowMapper<Repair> repairMapper() {
        return (rs, row) -> new Repair(rs.getString("repair_id"), rs.getString("cache_key"), rs.getString("state"),
                java.util.Arrays.asList(read(rs.getString("targets_json"), String[].class)), map(rs.getString("results_json")), map(rs.getString("errors_json")),
                rs.getString("owner_token"), rs.getLong("fence"), rs.getLong("lease_until"), rs.getString("trace_id"));
    }

    private boolean owns(Job current, Job owner) {
        return current != null && "RUNNING".equals(current.state()) && current.fence() == owner.fence()
                && owner.owner().equals(current.owner());
    }

    private RowMapper<Job> mapper() {
        return (rs, row) -> new Job(rs.getString("cache_key"), rs.getString("job_id"), rs.getString("source_key"),
                rs.getString("revision"), rs.getLong("actor_id"), rs.getString("state"),
                map(rs.getString("result_json")), map(rs.getString("errors_json")), rs.getString("owner_token"),
                rs.getLong("fence"), rs.getLong("lease_until"), rs.getLong("event_sequence"),
                rs.getInt("provider_calls"), rs.getLong("input_tokens"), rs.getLong("output_tokens"));
    }

    private Map<String, String> map(String value) {
        try {
            return json.readValue(value, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new NovelProblem("PERSISTED_DATA_INVALID", 500);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (Exception exception) {
            throw new NovelProblem("PERSISTED_DATA_INVALID", 500);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception exception) {
            throw new NovelProblem("PERSISTENCE_ENCODING_FAILED", 500);
        }
    }
}
