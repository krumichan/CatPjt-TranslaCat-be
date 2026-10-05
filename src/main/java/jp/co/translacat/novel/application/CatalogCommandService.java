package jp.co.translacat.novel.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.novel.domain.*;
import jp.co.translacat.novel.infrastructure.persistence.CatalogStore;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** 유료 작업은 명시 명령에서만 제출한다. 조회와 재연결은 저장 상태만 읽는다. */
@Service
public class CatalogCommandService {
    private final CatalogStore store;
    private final NovelRepository repository;
    private final NovelPorts.Ai ai;
    private final NovelExecutionSettings settings;
    private final FairAiScheduler scheduler;
    private final ObjectMapper mapper;

    public CatalogCommandService(CatalogStore store, NovelRepository repository, NovelPorts.Ai ai,
                                 NovelExecutionSettings settings, FairAiScheduler scheduler, ObjectMapper mapper) {
        this.store = store;
        this.repository = repository;
        this.ai = ai;
        this.settings = settings;
        this.scheduler = scheduler;
        this.mapper = mapper;
    }

    public String start(long actor, String platform, CatalogModels.Start request, String traceId) {
        if (request == null || request.selector() == null || request.source() == null) invalid();
        token(request.idempotencyKey());
        String normalized = new EpisodeKey(platform, "n1a", "1").platform();
        String selectorHash = hash(normalized + "|" + json(request.selector()));
        String policy = "catalog-v1|" + settings.options(ai).fingerprint();
        List<CatalogStore.Prepared> prepared = new ArrayList<>();

        // 순위/검색어는 cache identity에서 제외하고 원문과 확정 용어의 실제 내용을 포함한다.
        for (var card : request.source().cards()) {
            var glossary = repository.glossary(new EpisodeKey(normalized, card.identifier(), "1"), actor);
            String revision = hash(json(new TreeMap<>(card.text())));
            String key = hash(actor + "|" + normalized + "|" + card.identifier() + "|" + revision + "|"
                    + request.selector().language() + "|" + policy + "|" + json(new TreeMap<>(glossary.terms())));
            prepared.add(new CatalogStore.Prepared(key, revision, policy, card, glossary.terms()));
        }
        String id = store.create(actor, normalized, request.idempotencyKey(), selectorHash, request.selector(),
                request.source(), prepared, traceId);
        for (String key : store.pending(actor, id)) enqueue(actor, key);
        return id;
    }

    public void retry(long actor, String id, String itemId, String requestKey) {
        token(id);
        token(itemId);
        token(requestKey);
        enqueue(actor, store.retry(actor, id, itemId, requestKey));
    }

    public CatalogModels.Snapshot cancel(long actor, String id) {
        token(id);
        return store.cancel(actor, id);
    }

    private void enqueue(long actor, String key) {
        AtomicLong queueMs = new AtomicLong();
        // 목록 전체의 순차 대기는 유한 5분이다. 이 시간은 provider 실행 예산을 소진하지 않는다.
        long deadline = System.nanoTime() + CatalogExecutionBudget.QUEUE_MS * 1000000L;
        try {
            var future = scheduler.submitCatalog(Long.toString(actor), 4, deadline, queueMs::set, () -> {
                var work = store.claim(key, CatalogExecutionBudget.LEASE_MS);
                if (work == null) return null;
                try {
                    translate(work, queueMs.get());
                } catch (Exception failure) {
                    store.failed(work, failure instanceof NovelProblem p ? p.code() : "CATALOG_TRANSLATION_FAILED");
                }
                return null;
            });
            // 만료/취소된 queue도 정리한다. 유료 재시도는 이 observer에서 시작하지 않는다.
            Thread.startVirtualThread(() -> {
                try { future.get(); }
                catch (Exception failure) { store.rejectPending(key, "CATALOG_QUEUE_FAILED"); }
            });
        } catch (RuntimeException failure) {
            store.rejectPending(key, "CATALOG_QUEUE_FULL");
        }
    }

    private void translate(CatalogStore.Work work, long queueMs) {
        // 한 작품의 필드만 출력하되 제목/소개/작가의 전체 원문을 같은 호출 문맥에 둔다.
        List<SourceEpisode.Segment> items = new ArrayList<>();
        Map<String, String> result = new LinkedHashMap<>();
        for (String field : List.of("title", "author", "synopsis", "statusText")) {
            String raw = work.source().text().get(field);
            if (raw.isBlank()) { result.put(field, ""); continue; }
            items.add(new SourceEpisode.Segment(field, field, items.size(), items.size(), 0, raw.length(), raw, raw));
        }
        var options = settings.options(ai);
        long started = System.nanoTime();
        var translated = ai.translate(work.traceId(), UUID.randomUUID().toString(), items,
                items.stream().map(SourceEpisode.Segment::plainJa).toList(), work.glossary(),
                CatalogExecutionBudget.PROVIDER_MS, options.responseShape(),
                options.validationPolicy(), TranslationOptions.AnnotationPolicy.NONE);
        var expected = items.stream().map(SourceEpisode.Segment::id).collect(java.util.stream.Collectors.toSet());
        if (!translated.items().keySet().equals(expected)
                || translated.items().values().stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new NovelProblem("CATALOG_ALIGNMENT_INVALID", 502);
        }
        result.putAll(translated.items());
        Map<String, Object> diagnostics = new LinkedHashMap<>(translated.timings());
        diagnostics.put("queueMs", queueMs);
        diagnostics.put("queueBudgetMs", CatalogExecutionBudget.QUEUE_MS);
        diagnostics.put("providerBudgetMs", CatalogExecutionBudget.PROVIDER_MS);
        diagnostics.put("commitGraceMs", CatalogExecutionBudget.COMMIT_GRACE_MS);
        diagnostics.put("executionMs", (System.nanoTime() - started) / 1000000);
        diagnostics.put("inputTokens", translated.inputTokens());
        diagnostics.put("outputTokens", translated.outputTokens());

        // 저장 실패는 보유한 같은 결과로만 한 번 더 저장한다. provider 재호출은 하지 않는다.
        try { store.ready(work, result, diagnostics); }
        catch (org.springframework.dao.TransientDataAccessException failure) { store.ready(work, result, diagnostics); }
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception failure) { throw new IllegalArgumentException("Catalog identity invalid", failure); }
    }
    private static String hash(String value) { return SentenceSegmenter.hash(value); }
    private static void token(String value) {
        if (value == null || !value.matches("[A-Za-z0-9:_-]{1,128}")) invalid();
    }
    private static void invalid() { throw new NovelProblem("CATALOG_REQUEST_INVALID", 400); }
}
