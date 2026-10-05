package jp.co.translacat.novel.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 현재 HTTP 요청의 단조 시계 계측. 본문·인증 키·사용자 데이터는 기록하지 않는다. */
public final class RequestTrace {
    private static final ThreadLocal<RequestTrace> CURRENT = new ThreadLocal<>();
    private final String id;
    private final long started = System.nanoTime();
    private final Map<String, Object> values = new LinkedHashMap<>();

    private RequestTrace(String id) { this.id = id; values.put("sourceFetchIncluded", false); }
    public static RequestTrace begin(String supplied) {
        String id = supplied == null || supplied.isBlank() ? UUID.randomUUID().toString() : supplied;
        if (id.length() > 64 || !id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new jp.co.translacat.novel.domain.NovelProblem("TRACE_ID_INVALID", 400);
        }
        RequestTrace trace = new RequestTrace(id); CURRENT.set(trace); return trace;
    }
    public static RequestTrace current() { return CURRENT.get(); }
    public static String idOrNew() { return current() == null ? UUID.randomUUID().toString() : current().id; }
    public static void clear() { CURRENT.remove(); }
    public String id() { return id; }
    public static double elapsed(long start) { return Math.max(0, (System.nanoTime() - start) / 1_000_000.0); }
    public static void duration(String name, long start) { if (current() != null) current().add(name, elapsed(start)); }
    public void add(String name, double millis) { values.merge(name, millis, (a, b) -> ((Number) a).doubleValue() + ((Number) b).doubleValue()); }
    public static void fetchedSource() { if (current() != null) current().values.put("sourceFetchIncluded", true); }
    public Map<String, Object> snapshot() {
        var copy = new LinkedHashMap<>(values);
        copy.put("novelResponseAssemblyMs", elapsed(started));
        // 응답 직렬화/네트워크/브라우저 전달과 Provider 첫 토큰은 여기서 관측하지 않는다.
        copy.put("deliveryMs", null); copy.put("providerTtftMs", null);
        return copy;
    }
}
