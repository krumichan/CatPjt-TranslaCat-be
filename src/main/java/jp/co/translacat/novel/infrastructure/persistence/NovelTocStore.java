package jp.co.translacat.novel.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.novel.domain.NovelProblem;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;

/** 목차 검토 결과는 기존 episode 번역과 별도로 보존하고 같은 입력의 실행을 공유한다. */
@Repository
public class NovelTocStore {
    private static final TypeReference<Map<String, String>> STRINGS = new TypeReference<>() {};
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;

    public NovelTocStore(JdbcTemplate jdbc, ObjectMapper json, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.json = json;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public record Claim(String key, String owner, Map<String, String> ready) {
        public boolean claimed() { return owner != null; }
    }

    public Claim claim(String key, String work, String operation, String policy, String input) {
        // 계정 ID 없이 입력 fingerprint를 PK로 삼는다. 유료 실행 전에 DB에서 한 owner만 확정한다.
        String owner = UUID.randomUUID().toString();
        try {
            transaction.executeWithoutResult(status -> jdbc.update(
                    "INSERT INTO novel_toc_translation(cache_key,work_key,operation,policy_identity,state,owner,input_json,result_json,failure_code,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                    key, work, operation, policy, "RUNNING", owner, input, "{}", null,
                    System.currentTimeMillis(), System.currentTimeMillis()));
            return new Claim(key, owner, null);
        } catch (DuplicateKeyException existing) {
            return transaction.execute(status -> {
                var rows = jdbc.query("SELECT state,result_json FROM novel_toc_translation WHERE cache_key=? FOR UPDATE",
                        (row, index) -> Map.entry(row.getString(1), row.getString(2)), key);
                if (rows.isEmpty()) throw new NovelProblem("TOC_TRANSLATION_STATE_INVALID", 503);
                if ("READY".equals(rows.getFirst().getKey())) return new Claim(key, null, decode(rows.getFirst().getValue()));

                // 실패나 응답 불명 상태를 단순 GET으로 다시 과금하지 않는다.
                if ("RUNNING".equals(rows.getFirst().getKey())) {
                    throw new NovelProblem("TOC_TRANSLATION_IN_PROGRESS", 409, true, 1000);
                }
                throw new NovelProblem("TOC_TRANSLATION_REVIEW_FAILED", 503);
            });
        }
    }

    public void complete(Claim claim, Map<String, String> result) {
        transaction.executeWithoutResult(status -> {
            int saved = jdbc.update("UPDATE novel_toc_translation SET state='READY',result_json=?,owner=NULL,updated_at=? WHERE cache_key=? AND owner=? AND state='RUNNING'",
                    encode(result), System.currentTimeMillis(), claim.key(), claim.owner());
            if (saved == 1) return;

            // commit 성공 뒤 응답만 잃은 경우 같은 유료 결과의 저장 확인은 멱등하게 끝낸다.
            var rows = jdbc.query("SELECT state,result_json FROM novel_toc_translation WHERE cache_key=? FOR UPDATE",
                    (row, index) -> Map.entry(row.getString(1), row.getString(2)), claim.key());
            if (!rows.isEmpty() && "READY".equals(rows.getFirst().getKey())
                    && decode(rows.getFirst().getValue()).equals(result)) return;
            throw new NovelProblem("TOC_TRANSLATION_OWNERSHIP_LOST", 409);
        });
    }

    public void fail(Claim claim, String code) {
        transaction.executeWithoutResult(status -> jdbc.update(
                "UPDATE novel_toc_translation SET state='FAILED',failure_code=?,owner=NULL,updated_at=? WHERE cache_key=? AND owner=? AND state='RUNNING'",
                code != null && code.matches("[A-Z][A-Z0-9_]{1,79}") ? code : "TOC_TRANSLATION_FAILED",
                System.currentTimeMillis(), claim.key(), claim.owner()));
    }

    public String state(String key) {
        var states = jdbc.query("SELECT state FROM novel_toc_translation WHERE cache_key=?",
                (row, index) -> row.getString(1), key);
        return states.isEmpty() ? "MISSING" : states.getFirst();
    }

    public String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception invalid) { throw new NovelProblem("TOC_TRANSLATION_STATE_INVALID", 500); }
    }

    private Map<String, String> decode(String value) {
        try { return Map.copyOf(json.readValue(value, STRINGS)); }
        catch (Exception invalid) { throw new NovelProblem("TOC_TRANSLATION_STATE_INVALID", 500); }
    }
}
