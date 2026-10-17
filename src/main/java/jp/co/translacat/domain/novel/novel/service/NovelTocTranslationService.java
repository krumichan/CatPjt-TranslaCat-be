package jp.co.translacat.domain.novel.novel.service;

import jp.co.translacat.domain.novel.novel.model.NovelContext;
import jp.co.translacat.domain.novel.novel.model.RawEpisodeContext;
import jp.co.translacat.domain.novel.translation.model.TranslationUnit;
import jp.co.translacat.novel.application.FairAiScheduler;
import jp.co.translacat.novel.application.NovelExecutionSettings;
import jp.co.translacat.novel.application.NovelPorts;
import jp.co.translacat.novel.application.NovelRepository;
import jp.co.translacat.novel.application.RequestTrace;
import jp.co.translacat.novel.domain.EpisodeKey;
import jp.co.translacat.novel.domain.NovelProblem;
import jp.co.translacat.novel.domain.SentenceSegmenter;
import jp.co.translacat.novel.domain.SourceEpisode;
import jp.co.translacat.novel.domain.TranslationPolicy;
import jp.co.translacat.novel.infrastructure.ai.TitleReviewRequestFactory;
import jp.co.translacat.novel.infrastructure.persistence.NovelTocStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.LongFunction;

/** 작품의 원문·확정 번역을 목차 전체가 공유하고 기존 번역의 변경분만 별도로 보관한다. */
@Service
public class NovelTocTranslationService {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(NovelTocTranslationService.class);
    public static final String POLICY = "novel-toc-work-context-review-v1|title-output8192-v1";
    private static final long DEADLINE_MILLIS = 120_000;
    private final NovelTocStore store;
    private final NovelRepository repository;
    private final NovelPorts.Ai ai;
    private final NovelExecutionSettings settings;
    private final FairAiScheduler scheduler;

    public NovelTocTranslationService(NovelTocStore store, NovelRepository repository, NovelPorts.Ai ai,
                                      NovelExecutionSettings settings, FairAiScheduler scheduler) {
        this.store = store;
        this.repository = repository;
        this.ai = ai;
        this.settings = settings;
        this.scheduler = scheduler;
    }

    public List<RawEpisodeContext> resolve(EpisodeKey key, long actor, NovelContext work,
                                            List<RawEpisodeContext> episodes, List<TranslationUnit> dirty) {
        // 용어집 버전/사용자 ID 대신 실제 내용과 작품의 확정 번역을 공통 identity에 포함한다.
        var glossary = repository.glossary(key, actor, "ko").terms();
        var references = references(work);
        var context = List.of(text(work.getTitle()), text(work.getSynopsis()));
        String trace = RequestTrace.idOrNew();
        String policy = SentenceSegmenter.hash(String.join("|", POLICY, TitleReviewRequestFactory.POLICY,
                TranslationPolicy.version("ko"), "selective-repair-v1", settings.options(ai).fingerprint()));
        String workKey = key.platform() + ":" + key.novel();
        Map<TranslationUnit, Boolean> dirtySet = new IdentityHashMap<>();
        dirty.forEach(unit -> dirtySet.put(unit, true));
        var targets = episodes.stream().filter(episode -> dirtySet.containsKey(episode.getTitle())
                && !text(episode.getTitle()).isBlank()).toList();
        boolean hasLegacy = episodes.stream().anyMatch(episode -> !dirtySet.containsKey(episode.getTitle())
                && !text(episode.getTitle()).isBlank());

        // 신규/변경 제목도 소개의 원문·확정 번역 및 같은 용어집으로 생성한다.
        if (!targets.isEmpty()) {
            var segments = targets.stream().map(episode -> new SourceEpisode.Segment(episode.getIdentifier(),
                    "toc", episode.getSequence(), 0, 0, text(episode.getTitle()).length(),
                    text(episode.getTitle()), text(episode.getTitle()))).toList();
            var input = input(workKey, policy, segments.stream().map(segment -> new TreeMap<>(Map.of(
                    "id", segment.id(), "source", segment.plainJa()))).toList(), references, glossary);
            var generated = cached(workKey, policy, "GENERATE", input, remainingMillis -> {
                var options = settings.options(ai);
                var accepted = references.stream().map(reference -> Map.of("source", reference.source(),
                        "translation", reference.translation())).toList();
                var result = ai.translateTitles(trace, UUID.randomUUID().toString(), segments,
                        context, glossary, accepted, remainingMillis, options.responseShape(),
                        options.validationPolicy(), options.annotationPolicy(), "ko").items();
                validateExact(targets.stream().map(RawEpisodeContext::getIdentifier).toList(), result);
                return result;
            });
            validateExact(targets.stream().map(RawEpisodeContext::getIdentifier).toList(), generated);
            targets.forEach(episode -> episode.getTitle().setKo(generated.get(episode.getIdentifier())));
        }

        // 기존 episode 행에 검토 patch가 저장되지 않도록 응답용 overlay 적용 전에 사본을 고정한다.
        var storage = copy(episodes);
        var candidates = episodes.stream().filter(episode -> !text(episode.getTitle()).isBlank())
                .map(episode -> new NovelPorts.TitleCandidate(episode.getIdentifier(), text(episode.getTitle()),
                        episode.getTitle().getKo())).toList();
        if (candidates.isEmpty()) return storage;
        if (candidates.stream().anyMatch(candidate -> candidate.translation() == null || candidate.translation().isBlank())) {
            throw new NovelProblem("TOC_TRANSLATION_RESULT_INVALID", 502);
        }

        // 기존 번역은 일괄 재생성하지 않는다. 동일 입력의 검토는 한 번만 하고 변경 필요 ID만 받는다.
        var reviewInput = input(workKey, policy, candidates, references, glossary);
        Map<String, String> corrections;
        try {
            corrections = cached(workKey, policy, "REVIEW", reviewInput, remainingMillis -> {
                var result = hasLegacy ? ai.reviewTitles(trace, UUID.randomUUID().toString(), candidates,
                        references, glossary, remainingMillis, "ko").corrections() : Map.<String, String>of();
                validateCorrections(candidates, result);
                return result;
            });
            validateCorrections(candidates, corrections);
        } catch (NovelProblem reviewUnavailable) {
            // 선택적 정합 검토가 진행 중이거나 실패해도 이미 정상 저장된 목차의 조회를 막지 않는다.
            // 실패 이력을 유지하고 자동 재과금하지 않으며 DB 연결/권한 오류는 이 경로로 숨기지 않는다.
            String reviewKey = SentenceSegmenter.hash("REVIEW|" + store.encode(reviewInput));
            LOG.warn("Novel TOC review unavailable: work={}, state={}, code={}",
                    workKey, store.state(reviewKey), reviewUnavailable.code());
            return storage;
        }
        episodes.forEach(episode -> {
            String corrected = corrections.get(episode.getIdentifier());
            if (corrected != null) episode.getTitle().setKo(corrected);
        });
        return storage;
    }

    private Map<String, String> cached(String work, String policy, String operation,
                                        Map<String, Object> input, LongFunction<Map<String, String>> action) {
        String encoded = store.encode(input);
        var claim = store.claim(SentenceSegmenter.hash(operation + "|" + encoded), work, operation, policy, encoded);
        if (!claim.claimed()) return claim.ready();
        try {
            // provider 실행 동안 DB 잠금을 보유하지 않고 기존 소설의 유한 대기열에 참여한다.
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(DEADLINE_MILLIS);
            var future = scheduler.submitCatalog("toc:" + work, 1, deadline, ignored -> {},
                    () -> action.apply(remainingMillis(deadline)));
            Map<String, String> result;
            try {
                // 큐에서 보낸 시간을 provider와 호출자 양쪽의 동일한 실행 기한에서 차감한다.
                result = Map.copyOf(future.get(remainingMillis(deadline), TimeUnit.MILLISECONDS));
            } catch (Exception failure) {
                future.cancel(true);
                if (failure instanceof NovelProblem problem) throw problem;
                if (failure instanceof ExecutionException execution && execution.getCause() instanceof RuntimeException cause) throw cause;
                if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new NovelProblem("TOC_TRANSLATION_EXECUTION_FAILED", 502);
            }
            try {
                store.complete(claim, result);
            } catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException transientSaveFailure) {
                // 이미 받은 바이트/결과를 유지하고 DB 확정만 한 번 더 시도한다. provider는 다시 호출하지 않는다.
                store.complete(claim, result);
            }
            return result;
        } catch (RuntimeException failure) {
            store.fail(claim, failure instanceof NovelProblem problem ? problem.code() : "TOC_TRANSLATION_FAILED");
            throw failure;
        }
    }

    private long remainingMillis(long deadline) {
        long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        if (remaining < 1) throw new NovelProblem("JOB_DEADLINE_EXCEEDED", 504);
        return remaining;
    }

    private Map<String, Object> input(String work, String policy, Object titles,
                                       List<NovelPorts.TitleReference> references, Map<String, String> glossary) {
        var input = new LinkedHashMap<String, Object>();
        input.put("policy", policy);
        input.put("work", work);
        input.put("language", "ko");
        input.put("titles", titles);
        input.put("references", references);
        input.put("glossary", new TreeMap<>(glossary));
        return input;
    }

    private List<NovelPorts.TitleReference> references(NovelContext work) {
        var references = new ArrayList<NovelPorts.TitleReference>();
        for (TranslationUnit unit : List.of(work.getTitle(), work.getSynopsis())) {
            if (!text(unit).isBlank() && unit.getKo() != null && !unit.getKo().isBlank()) {
                references.add(new NovelPorts.TitleReference(text(unit), unit.getKo()));
            }
        }
        return List.copyOf(references);
    }

    private void validateExact(List<String> ids, Map<String, String> result) {
        if (!result.keySet().equals(Set.copyOf(ids)) || result.values().stream().anyMatch(value ->
                value == null || value.isBlank() || value.length() > 2000)) {
            throw new NovelProblem("TOC_TRANSLATION_RESULT_INVALID", 502);
        }
    }

    private void validateCorrections(List<NovelPorts.TitleCandidate> candidates, Map<String, String> result) {
        var ids = candidates.stream().map(NovelPorts.TitleCandidate::id).collect(java.util.stream.Collectors.toSet());
        if (!ids.containsAll(result.keySet()) || result.values().stream().anyMatch(value ->
                value == null || value.isBlank() || value.length() > 2000)) {
            throw new NovelProblem("TOC_REVIEW_RESULT_INVALID", 502);
        }
    }

    private List<RawEpisodeContext> copy(List<RawEpisodeContext> episodes) {
        return episodes.stream().map(episode -> RawEpisodeContext.builder().identifier(episode.getIdentifier())
                .sequence(episode.getSequence()).title(TranslationUnit.of(episode.getTitle().getRawJa(),
                        episode.getTitle().getJa(), episode.getTitle().getKo())).build()).toList();
    }

    private String text(TranslationUnit unit) { return unit == null || unit.getRawJa() == null ? "" : unit.getRawJa(); }
}
