package jp.co.translacat.novel.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.novel.domain.CatalogModels;
import jp.co.translacat.novel.domain.CatalogExecutionBudget;
import jp.co.translacat.novel.domain.NovelProblem;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 작품 번역은 위치와 독립적으로 저장하고 목록마다 도착 순서를 영속화한다. */
@Repository
public class CatalogStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate transaction;
    private static final TypeReference<Map<String, String>> STRINGS = new TypeReference<>() {};

    public CatalogStore(JdbcTemplate jdbc, ObjectMapper mapper, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.transaction = new TransactionTemplate(manager);
    }

    public record Prepared(String cacheKey, String revision, String policy, CatalogModels.SourceCard source,
                           Map<String, String> glossary) {}
    public record Work(String cacheKey, long actor, String language, CatalogModels.SourceCard source,
                       Map<String, String> glossary, String owner, long fence, String traceId) {}
    private record Page(String id, long actor, CatalogModels.Selector selector, CatalogModels.SourcePage source,
                        boolean cancelled, long sequence, String traceId, String selectorHash) {}

    public synchronized String create(long actor, String platform, String requestKey, String selectorHash,
                                      CatalogModels.Selector selector, CatalogModels.SourcePage source,
                                      List<Prepared> prepared, String traceId) {
        var existing = jdbc.query("SELECT snapshot_id,selector_hash FROM novel_catalog_snapshot WHERE actor_id=? AND request_key=?",
                (row, index) -> Map.entry(row.getString(1), row.getString(2)), actor, requestKey);
        if (!existing.isEmpty()) {
            if (!existing.getFirst().getValue().equals(selectorHash)) throw new NovelProblem("IDEMPOTENCY_CONFLICT", 409);
            return existing.getFirst().getKey();
        }

        // 목록 생성과 item 연결은 한 transaction이다. unique key는 다른 프로세스의 중복 명령도 막는다.
        String id = UUID.randomUUID().toString();
        try {
            transaction.executeWithoutResult(status -> {
                jdbc.update("INSERT INTO novel_catalog_snapshot(snapshot_id,actor_id,platform,selector_hash,selector_json,source_json,request_key,cancelled,event_sequence,trace_id,created_at) VALUES(?,?,?,?,?,?,?,FALSE,0,?,?)",
                        id, actor, platform, selectorHash, json(selector), json(source), requestKey, traceId, System.currentTimeMillis());
                for (Prepared item : prepared) {
                    var states = jdbc.query("SELECT state FROM novel_catalog_translation WHERE cache_key=?",
                            (row, index) -> row.getString(1), item.cacheKey());
                    boolean cached = !states.isEmpty() && "READY".equals(states.getFirst());
                    if (states.isEmpty()) {
                        boolean original = selector.language().equals("ja");
                        try {
                            jdbc.update("INSERT INTO novel_catalog_translation(cache_key,actor_id,platform,work_id,source_revision,language,policy_identity,source_json,glossary_json,state,result_json,trace_id,diagnostics_json,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                                    item.cacheKey(), actor, platform, item.source().identifier(), item.revision(), selector.language(),
                                    item.policy(), json(item.source()), json(item.glossary()), original ? "READY" : "PENDING",
                                    json(original ? item.source().text() : Map.of()), traceId, "{}", System.currentTimeMillis());
                        } catch (DuplicateKeyException race) {
                            // 다른 목록이 같은 작품을 먼저 등록했다. 동일 cache key만 연결한다.
                        }
                    }
                    jdbc.update("INSERT INTO novel_catalog_item(snapshot_id,item_id,cache_key,actual_rank,source_position,cached) VALUES(?,?,?,?,?,?)",
                            id, item.source().identifier(), item.cacheKey(), item.source().rank(), item.source().sourcePosition(), cached);
                }
                // 준비된 캐시는 작업 제출 전에 먼저 도착 처리한다. 실제 rank로 정렬하지 않는다.
                for (Prepared item : prepared) {
                    String state = jdbc.queryForObject("SELECT state FROM novel_catalog_translation WHERE cache_key=?", String.class, item.cacheKey());
                    if (List.of("READY", "FAILED").contains(state)) publishToSnapshot(id, item.cacheKey());
                }
            });
        } catch (DuplicateKeyException race) {
            return create(actor, platform, requestKey, selectorHash, selector, source, prepared, traceId);
        }
        return id;
    }

    public List<String> pending(long actor, String snapshotId) {
        page(actor, snapshotId);
        return jdbc.query("SELECT t.cache_key FROM novel_catalog_item i JOIN novel_catalog_translation t ON t.cache_key=i.cache_key JOIN novel_catalog_snapshot s ON s.snapshot_id=i.snapshot_id WHERE i.snapshot_id=? AND s.cancelled=FALSE AND t.state='PENDING' ORDER BY i.source_position",
                (row, index) -> row.getString(1), snapshotId);
    }

    public synchronized Work claim(String key, long deadlineMs) {
        return transaction.execute(status -> claimInTransaction(key, deadlineMs));
    }

    private Work claimInTransaction(String key, long deadlineMs) {
        // 소비자가 없어진 대기 작업은 provider를 시작하지 않는다.
        if (subscribers(key) == 0) return null;
        String owner = UUID.randomUUID().toString();
        int changed = jdbc.update("UPDATE novel_catalog_translation SET state='RUNNING',owner_token=?,fence=fence+1,lease_until=?,attempt_count=attempt_count+1,updated_at=? WHERE cache_key=? AND state='PENDING' AND attempt_count<3",
                owner, System.currentTimeMillis() + deadlineMs, System.currentTimeMillis(), key);
        if (changed == 0) return null;
        publish(key);
        return jdbc.queryForObject("SELECT * FROM novel_catalog_translation WHERE cache_key=?", (row, index) ->
                new Work(key, row.getLong("actor_id"), row.getString("language"), read(row.getString("source_json"), CatalogModels.SourceCard.class),
                        strings(row.getString("glossary_json")), owner, row.getLong("fence"), row.getString("trace_id")), key);
    }

    public int subscribers(String key) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM novel_catalog_item i JOIN novel_catalog_snapshot s ON s.snapshot_id=i.snapshot_id WHERE i.cache_key=? AND s.cancelled=FALSE", Integer.class, key);
        return count == null ? 0 : count;
    }

    public synchronized boolean ready(Work work, Map<String, String> result, Map<String, Object> diagnostics) {
        return Boolean.TRUE.equals(transaction.execute(status -> {
            int changed = jdbc.update("UPDATE novel_catalog_translation SET state='READY',result_json=?,error_code=NULL,owner_token=NULL,lease_until=0,diagnostics_json=?,updated_at=? WHERE cache_key=? AND state='RUNNING' AND owner_token=? AND fence=?",
                    json(result), json(diagnostics), System.currentTimeMillis(), work.cacheKey(), work.owner(), work.fence());
            if (changed == 1) publish(work.cacheKey());
            return changed == 1;
        }));
    }

    public synchronized void failed(Work work, String code) {
        transaction.executeWithoutResult(status -> {
            int changed = jdbc.update("UPDATE novel_catalog_translation SET state='FAILED',error_code=?,owner_token=NULL,lease_until=0,updated_at=? WHERE cache_key=? AND state='RUNNING' AND owner_token=? AND fence=?",
                    code, System.currentTimeMillis(), work.cacheKey(), work.owner(), work.fence());
            if (changed == 1) publish(work.cacheKey());
        });
    }

    public synchronized void rejectPending(String key, String code) {
        transaction.executeWithoutResult(status -> {
            if (jdbc.update("UPDATE novel_catalog_translation SET state='FAILED',error_code=? WHERE cache_key=? AND state='PENDING'", code, key) == 1) publish(key);
        });
    }

    public synchronized String retry(long actor, String id, String itemId, String requestKey) {
        Page page = page(actor, id);
        if (page.cancelled()) throw new NovelProblem("CATALOG_CANCELLED", 409);
        var keys = jdbc.query("SELECT cache_key FROM novel_catalog_item WHERE snapshot_id=? AND item_id=?", (row, index) -> row.getString(1), id, itemId);
        if (keys.isEmpty()) throw new NovelProblem("CATALOG_ITEM_NOT_FOUND", 404);
        String key = keys.getFirst();
        String target = id + ":" + itemId;
        transaction.executeWithoutResult(status -> {
            var prior = jdbc.query("SELECT target_key FROM novel_catalog_retry WHERE actor_id=? AND request_key=?", (row, index) -> row.getString(1), actor, requestKey);
            if (!prior.isEmpty()) {
                if (!prior.getFirst().equals(target)) throw new NovelProblem("IDEMPOTENCY_CONFLICT", 409);
                return;
            }
            Integer exhausted = jdbc.queryForObject("SELECT COUNT(*) FROM novel_catalog_translation WHERE cache_key=? AND state='FAILED' AND attempt_count>=3", Integer.class, key);
            if (exhausted != null && exhausted > 0) throw new NovelProblem("CATALOG_RETRY_LIMIT", 409);
            jdbc.update("INSERT INTO novel_catalog_retry(actor_id,request_key,target_key) VALUES(?,?,?)", actor, requestKey, target);
            // READY와 RUNNING은 그대로 반환한다. 실패 카드 하나만 명시 명령으로 재개한다.
            int changed = jdbc.update("UPDATE novel_catalog_translation SET state='PENDING',error_code=NULL,updated_at=? WHERE cache_key=? AND state='FAILED' AND attempt_count<3", System.currentTimeMillis(), key);
            if (changed == 1) publish(key);
        });
        return key;
    }

    public synchronized CatalogModels.Snapshot cancel(long actor, String id) {
        page(actor, id);
        jdbc.update("UPDATE novel_catalog_snapshot SET cancelled=TRUE,event_sequence=event_sequence+1 WHERE snapshot_id=? AND cancelled=FALSE", id);
        return snapshot(actor, id);
    }

    public synchronized CatalogModels.Snapshot snapshot(long actor, String id) {
        page(actor, id);
        // 프로세스 중단으로 끝나지 않은 lease를 실패로 노출한다. GET에서는 새 유료 작업을 제출하지 않는다.
        transaction.executeWithoutResult(status -> {
            var queued = jdbc.query("SELECT t.cache_key FROM novel_catalog_translation t JOIN novel_catalog_item i ON i.cache_key=t.cache_key WHERE i.snapshot_id=? AND t.state='PENDING' AND t.updated_at<?",
                    (row, index) -> row.getString(1), id, System.currentTimeMillis() - CatalogExecutionBudget.QUEUE_MS);
            for (String key : queued) {
                int changed = jdbc.update("UPDATE novel_catalog_translation SET state='FAILED',error_code='CATALOG_QUEUE_INTERRUPTED' WHERE cache_key=? AND state='PENDING' AND updated_at<?", key, System.currentTimeMillis() - CatalogExecutionBudget.QUEUE_MS);
                if (changed == 1) publish(key);
            }
            var abandoned = jdbc.query("SELECT t.cache_key FROM novel_catalog_translation t JOIN novel_catalog_item i ON i.cache_key=t.cache_key WHERE i.snapshot_id=? AND t.state='RUNNING' AND t.lease_until<?",
                    (row, index) -> row.getString(1), id, System.currentTimeMillis());
            for (String key : abandoned) {
                int changed = jdbc.update("UPDATE novel_catalog_translation SET state='FAILED',error_code='CATALOG_EXECUTION_INTERRUPTED',owner_token=NULL,fence=fence+1,lease_until=0 WHERE cache_key=? AND state='RUNNING' AND lease_until<?", key, System.currentTimeMillis());
                if (changed == 1) publish(key);
            }
        });
        return transaction.execute(status -> snapshotInTransaction(actor, id));
    }

    private CatalogModels.Snapshot snapshotInTransaction(long actor, String id) {
        // 도착 번호와 본문을 같은 snapshot lock 아래 읽어 cursor보다 새로운 본문이 앞서가지 않게 한다.
        jdbc.queryForObject("SELECT snapshot_id FROM novel_catalog_snapshot WHERE snapshot_id=? AND actor_id=? FOR UPDATE", String.class, id, actor);
        Page page = page(actor, id);
        List<CatalogModels.Item> items = new ArrayList<>();
        int[] counts = new int[3];
        jdbc.query("SELECT i.*,t.state,t.source_json,t.result_json,t.error_code FROM novel_catalog_item i JOIN novel_catalog_translation t ON t.cache_key=i.cache_key WHERE i.snapshot_id=? ORDER BY CASE WHEN i.arrival_sequence IS NULL THEN 1 ELSE 0 END,i.arrival_sequence,i.source_position", row -> {
            counts[0]++;
            String state = row.getString("state");
            if (state.equals("READY")) counts[1]++;
            if (state.equals("FAILED")) counts[2]++;
            long arrival = row.getLong("arrival_sequence");
            if (row.wasNull()) return;
            var source = read(row.getString("source_json"), CatalogModels.SourceCard.class);
            Map<String, String> result = strings(row.getString("result_json"));
            items.add(new CatalogModels.Item(row.getString("item_id"), source.identifier(), row.getInt("actual_rank"),
                    row.getInt("source_position"), state, text(source.title(), result.get("title"), page.selector().language()),
                    text(source.author(), result.get("author"), page.selector().language()), text(source.synopsis(), result.get("synopsis"), page.selector().language()),
                    text(source.statusText(), result.get("statusText"), page.selector().language()), source.genreText(), source.isShortStory(),
                    arrival, row.getString("error_code"), row.getBoolean("cached")));
        }, id);
        String state = page.cancelled() ? "CANCELLED" : counts[1] == counts[0] ? "SUCCEEDED"
                : counts[1] + counts[2] == counts[0] ? "PARTIAL" : "RUNNING";
        return new CatalogModels.Snapshot(id, page.sequence(), state, counts[0], counts[1], counts[2],
                page.source().pageInfo(), page.selector(), List.copyOf(items), page.traceId(), page.source().sourceFetchMs());
    }

    private Page page(long actor, String id) {
        var pages = jdbc.query("SELECT * FROM novel_catalog_snapshot WHERE snapshot_id=? AND actor_id=?", (row, index) ->
                new Page(id, actor, read(row.getString("selector_json"), CatalogModels.Selector.class), read(row.getString("source_json"), CatalogModels.SourcePage.class),
                        row.getBoolean("cancelled"), row.getLong("event_sequence"), row.getString("trace_id"), row.getString("selector_hash")), id, actor);
        if (pages.isEmpty()) throw new NovelProblem("CATALOG_NOT_FOUND", 404);
        return pages.getFirst();
    }

    private void publish(String key) {
        var snapshots = jdbc.query("SELECT snapshot_id FROM novel_catalog_item WHERE cache_key=?", (row, index) -> row.getString(1), key);
        for (String id : snapshots) publishToSnapshot(id, key);
    }

    private void publishToSnapshot(String id, String key) {
        // row update가 snapshot의 도착 번호 할당을 직렬화한다. retry는 기존 번호를 유지한다.
        jdbc.update("UPDATE novel_catalog_snapshot SET event_sequence=event_sequence+1 WHERE snapshot_id=?", id);
        Long next = jdbc.queryForObject("SELECT event_sequence FROM novel_catalog_snapshot WHERE snapshot_id=?", Long.class, id);
        String state = jdbc.queryForObject("SELECT state FROM novel_catalog_translation WHERE cache_key=?", String.class, key);
        if (List.of("READY", "FAILED").contains(state)) {
            jdbc.update("UPDATE novel_catalog_item SET arrival_sequence=COALESCE(arrival_sequence,?) WHERE snapshot_id=? AND cache_key=?", next, id, key);
        }
    }

    private CatalogModels.Text text(String raw, String translated, String language) {
        return new CatalogModels.Text(raw, raw, language.equals("ko") ? translated : null);
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception failure) { throw new IllegalStateException("Catalog serialization failed", failure); }
    }
    private <T> T read(String value, Class<T> type) {
        try { return mapper.readValue(value, type); }
        catch (Exception failure) { throw new IllegalStateException("Catalog stored value invalid", failure); }
    }
    private Map<String, String> strings(String value) {
        try { return mapper.readValue(value, STRINGS); }
        catch (Exception failure) { throw new IllegalStateException("Catalog stored translation invalid", failure); }
    }
}
