package jp.co.translacat.novel.application;

import jp.co.translacat.novel.domain.TranslationPlanner;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 하나의 lease 실행 계측을 직렬 저장한다. 원문/번역/인증 정보를 포함하지 않는다. */
final class JobTelemetry {
    private final NovelRepository store;
    private final NovelRepository.Job owner;
    private final long started;
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final List<Map<String, Object>> chunks = new ArrayList<>();

    JobTelemetry(NovelRepository store, NovelRepository.Job owner, String traceId, long started,
                 TranslationPlanner.Plan plan, double planningMs, int globalLimit, long deadlineMillis,
                 String profileIdentity) {
        this.store = store;
        this.owner = owner;
        this.started = started;

        // 요청값과 실제 계획·실행 상한을 분리하여 선택된 정책 identity와 함께 고정한다.
        values.put("traceId", traceId);
        values.put("telemetryVersion", "novel-spans-v1");
        values.put("policyFingerprint", plan.policyFingerprint());
        values.put("profileIdentity", profileIdentity);
        values.put("requestedN", plan.requestedN());
        values.put("actualN", plan.actualN());
        values.put("requestedC", plan.requestedC());
        values.put("effectiveC", Math.min(plan.effectiveC(), globalLimit));
        values.put("globalConcurrencyLimit", globalLimit);
        values.put("planner", plan.strategy());
        values.put("plannerVersion", plan.plannerVersion());
        values.put("contextPolicy", plan.contextPolicy());
        values.put("responseShape", plan.responseShape());
        values.put("segmentationVersion", plan.segmentationVersion());
        values.put("validationPolicy", plan.validationPolicy());
        values.put("annotationPolicy", plan.annotationPolicy());
        values.put("schemaVersion", jp.co.translacat.novel.domain.TranslationOptions.ResponseShape.valueOf(plan.responseShape()).schemaVersion());
        values.put("adjustmentReasons", plan.adjustmentReasons());
        values.put("planningMs", planningMs);
        values.put("executionDeadlineMs", deadlineMillis);
        values.put("sourceCodePoints", plan.sourceCodePoints());
        values.put("lengthPolicy", plan.lengthPolicy());
        values.put("lengthPolicyVersion", plan.lengthPolicyVersion());
        values.put("lengthBand", plan.lengthBand());

        // 관측하지 않은 구간은 null로 두고 과거 성공량을 새 실행의 성과와 구분한다.
        values.put("providerTtftMs", null);
        values.put("deliveryMs", null);
        values.put("durableCompleteMs", null);
        values.put("priorCompletedSegments", owner.results().size());

        // 원문 본문 없이 청크별 계획량·문맥 선택 범위와 시도 목록만 보관한다.
        for (var chunk : plan.chunks()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("index", chunk.index());
            entry.put("targetSegments", chunk.targets().size());
            entry.put("sourceCodePoints", chunk.sourceCodePoints());
            entry.put("contextSegments", chunk.context().size());
            entry.put("contextSelection", chunk.contextSelection());
            entry.put("estimatedInputTokens", chunk.estimatedInputTokens());
            entry.put("estimatedOutputTokens", chunk.estimatedOutputTokens());
            entry.put("attempts", new ArrayList<>());
            chunks.add(entry);
        }
        values.put("chunks", chunks);
        publish();
    }

    synchronized void attempt(int chunk, Map<String, Object> attempt) {
        // 동시에 끝난 시도가 서로의 계측을 덮지 않도록 복사·직렬 저장한다.
        @SuppressWarnings("unchecked") var attempts = (List<Map<String, Object>>) chunks.get(chunk).get("attempts");
        attempts.add(new LinkedHashMap<>(attempt));
        publish();
    }

    synchronized void chunkCommitted(int chunk, double commitMs, boolean accepted) {
        chunks.get(chunk).put("commitMs", commitMs);
        chunks.get(chunk).put("commitAccepted", accepted);
        chunks.get(chunk).put("committedAtMs", RequestTrace.elapsed(started));
        publish();
    }

    synchronized void completed(double finishCommitMs, boolean durable) {
        // 성공 상태 commit 반환 뒤의 관측 시각이며 FE 표시 완료나 응답 전달 시간은 아니다.
        values.put("finishCommitMs", finishCommitMs);
        values.put("jobElapsedMs", RequestTrace.elapsed(started));
        if (durable) values.put("durableCompleteMs", RequestTrace.elapsed(started));
        publish();
    }

    // 저장소가 owner/fence를 검사하므로 취소·소유권 변경 후 계측도 이전 실행에서 덮지 않는다.
    private void publish() { store.saveDiagnostics(owner, values); }
}
