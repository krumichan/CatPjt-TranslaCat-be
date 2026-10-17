package jp.co.translacat.novel.application;

import jp.co.translacat.novel.domain.EpisodeKey;
import jp.co.translacat.novel.domain.NovelProblem;
import jp.co.translacat.novel.domain.SourceEpisode;
import jp.co.translacat.novel.domain.TranslationPolicy;
import jp.co.translacat.novel.infrastructure.japanese.NovelRubyFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.Semaphore;

@Service
public class NovelQueryService {
    private final NovelRepository store;
    private final NovelPorts.Source sourcePort;
    private final NovelPorts.Ai ai;
    private final long ttlMillis;
    private final NovelExecutionSettings settings;
    private final NovelRubyFormatter ruby;
    private final Object[] fetchLocks = java.util.stream.IntStream.range(0, 64).mapToObj(ignored -> new Object()).toArray();
    private final Semaphore sourceSlots = new Semaphore(4);

    public NovelQueryService(NovelRepository store, NovelPorts.Source sourcePort, NovelPorts.Ai ai, long ttlSeconds) {
        this(store, sourcePort, ai, ttlSeconds, NovelExecutionSettings.baseline(), null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public NovelQueryService(NovelRepository store, NovelPorts.Source sourcePort, NovelPorts.Ai ai,
                             @Value("${novel.source.ttl-seconds:600}") long ttlSeconds, NovelExecutionSettings settings,
                             NovelRubyFormatter ruby) {
        this.store = store;
        this.sourcePort = sourcePort;
        this.ai = ai;
        this.ttlMillis = Math.clamp(ttlSeconds, 1, 86400) * 1000;
        this.settings = settings;
        this.ruby = ruby;
    }

    public ReaderSnapshot reader(EpisodeKey key, long actorId) {
        return reader(key, actorId, "ko");
    }

    public ReaderSnapshot reader(EpisodeKey key, long actorId, String targetLanguage) {
        String target = jp.co.translacat.novel.domain.NovelTargetLanguage.normalize(targetLanguage);
        SourceEpisode source = source(key);
        String glossaryVersion = store.glossary(key, actorId, target).version();
        long started = System.nanoTime();
        String cacheKey = cacheKey(key, source, actorId, glossaryVersion, resolve(source), target);
        NovelRepository.Job job = store.byCache(cacheKey).orElse(null);
        job = share(key, source, actorId, glossaryVersion, target, job);
        if (job != null) {
            job = store.expireAbandoned(job, source);
            job = store.syncShared(job, source);
        }
        RequestTrace.duration("dbCacheMs", started);
        return snapshot(key, source, job, glossaryVersion, target);
    }

    public SourceEpisode source(EpisodeKey key) {
        long cacheStarted = System.nanoTime();
        NovelRepository.StoredSource existing = store.current(key).orElse(null);
        RequestTrace.duration("dbCacheMs", cacheStarted);
        if (existing != null) requireSegmentationPolicy(existing.source());
        if (existing != null && System.currentTimeMillis() - existing.fetchedAt() < ttlMillis) {
            return existing.source();
        }
        if (!sourceSlots.tryAcquire()) {
            throw new NovelProblem("SOURCE_QUEUE_FULL", 429, true, 1000);
        }
        Object lock = fetchLocks[Math.floorMod(key.value().hashCode(), fetchLocks.length)];
        long sourceQueueStarted = System.nanoTime();
        try {
            synchronized (lock) {
                RequestTrace.duration("sourceQueueMs", sourceQueueStarted);
                // 같은 회차의 재검증도 저장 snapshot을 다시 확인한 후 수행한다.
                existing = store.current(key).orElse(null);
                if (existing != null) requireSegmentationPolicy(existing.source());
                if (existing != null && System.currentTimeMillis() - existing.fetchedAt() < ttlMillis) {
                    return existing.source();
                }
                long started = System.currentTimeMillis();
                RequestTrace.fetchedSource();
                long fetchStarted = System.nanoTime();
                SourceEpisode fetched = sourcePort.fetch(key);
                requireSegmentationPolicy(fetched);
                RequestTrace.duration("sourceFetchParseMs", fetchStarted);
                long commitStarted = System.nanoTime();
                SourceEpisode saved = store.saveSource(key, fetched, started);
                RequestTrace.duration("sourceCommitMs", commitStarted);
                return saved;
            }
        } finally {
            sourceSlots.release();
        }
    }

    public ReaderSnapshot status(EpisodeKey key, long actorId, String jobId) {
        long started = System.nanoTime();
        NovelRepository.Job job = store.byId(key, jobId, actorId);
        SourceEpisode current = requireCurrentRevision(key, job.revision());
        String glossaryVersion = store.glossary(key, actorId, job.targetLanguage()).version();
        String expectedCache = cacheKey(key, current, actorId, glossaryVersion, resolve(current), job.targetLanguage());
        if (!job.cacheKey().equals(expectedCache)) {
            throw new NovelProblem("TRANSLATION_POLICY_CHANGED", 409);
        }
        job = share(key, current, actorId, glossaryVersion, job.targetLanguage(), job);
        job = store.expireAbandoned(job, current);
        job = store.syncShared(job, current);
        RequestTrace.duration("dbCacheMs", started);
        return snapshot(key, current, job, glossaryVersion);
    }

    public SourceEpisode requireCurrentRevision(EpisodeKey key, String revision) {
        long started = System.nanoTime();
        SourceEpisode source = store.current(key).orElseThrow(() -> new NovelProblem("SOURCE_NOT_LOADED", 409)).source();
        requireSegmentationPolicy(source);
        RequestTrace.duration("dbCacheMs", started);
        if (revision == null || !source.revision().equals(revision)) {
            throw new NovelProblem("SOURCE_REVISION_CHANGED", 409);
        }
        return source;
    }

    public NovelRepository.Glossary glossary(EpisodeKey key, long actorId) {
        return store.glossary(key, actorId);
    }

    public NovelRepository.Glossary glossary(EpisodeKey key, long actorId, String targetLanguage) {
        return store.glossary(key, actorId, targetLanguage);
    }

    public String cacheKey(EpisodeKey key, SourceEpisode source, long actor, String glossary) {
        return cacheKey(key, source, actor, glossary, resolve(source));
    }
    public String cacheKey(EpisodeKey key, SourceEpisode source, long actor, String glossary,
                           jp.co.translacat.novel.domain.TranslationLengthPolicy.Resolution selection) {
        return cacheKey(key, source, actor, glossary, selection, "ko");
    }

    public String cacheKey(EpisodeKey key, SourceEpisode source, long actor, String glossary,
                           jp.co.translacat.novel.domain.TranslationLengthPolicy.Resolution selection, String targetLanguage) {
        requireSegmentationPolicy(source);
        if (!source.revision().equals(selection.sourceRevision())) throw new NovelProblem("SOURCE_REVISION_CHANGED", 409);
        return new TranslationPolicy().cacheKey(key, source, actor, ai.model(), glossary, selection.options(), targetLanguage);
    }

    public NovelRepository.Job share(EpisodeKey key, SourceEpisode source, long actor, String glossary,
                                    String target, NovelRepository.Job job) {
        return share(key, source, actor, glossary, target, job, false);
    }

    public NovelRepository.Job share(EpisodeKey key, SourceEpisode source, long actor, String glossary,
                                    String target, NovelRepository.Job job, boolean explicitResume) {
        var selection = resolve(source);
        var policy = new TranslationPolicy();
        String identity = policy.sharedCacheKey(key, source, ai.model(), glossary, selection.options(), target);

        // 과거 계정별 성공본도 저장 당시 키를 재계산해 정책·용어집이 정확히 같은 경우에만 연결한다.
        NovelRepository.Job common = store.sharedCompleted(identity).orElse(null);
        if (common == null) {
            for (var candidate : store.completedCandidates(key, source.revision(), target)) {
                String expected = policy.cacheKey(key, source, candidate.actorId(), ai.model(), glossary, selection.options(), target);
                if (candidate.cacheKey().equals(expected)) {
                    store.publishShared(identity, candidate, source);
                    common = store.sharedCompleted(identity).orElse(null);
                    if (common != null) break;
                }
            }
        }

        // 조회 자체는 AI를 시작하지 않는다. 완료본이 있을 때만 계정 소유의 조회용 작업을 만든다.
        if (job == null && common != null) {
            String actorKey = cacheKey(key, source, actor, glossary, selection, target);
            job = store.create(key, source, actor, actorKey, "shared:" + identity, target);
        }
        return job == null ? null : store.attachShared(identity, job, source, explicitResume);
    }
    public jp.co.translacat.novel.domain.TranslationLengthPolicy.Resolution resolve(SourceEpisode source) {
        requireSegmentationPolicy(source);
        return jp.co.translacat.novel.domain.TranslationLengthPolicy.resolve(source, settings.options(ai));
    }
    private void requireSegmentationPolicy(SourceEpisode source) {
        if (!source.segmentationVersion().equals(settings.options(ai).segmentationPolicy().version())) {
            // Opt-in experiments need a separate source namespace/DB; never re-segment stored data implicitly.
            throw new NovelProblem("SOURCE_SEGMENTATION_POLICY_MISMATCH", 409);
        }
    }
    public ReaderSnapshot snapshot(EpisodeKey key, SourceEpisode source, NovelRepository.Job job, String glossary) {
        return snapshot(key, source, job, glossary, job == null ? "ko" : job.targetLanguage());
    }

    public ReaderSnapshot snapshot(EpisodeKey key, SourceEpisode source, NovelRepository.Job job, String glossary, String targetLanguage) {
        var repair = job == null ? null : store.latestRepair(job).orElse(null);
        ReaderSnapshot snapshot = ReaderSnapshot.of(key, source, job, glossary, targetLanguage).withDiagnostics(job == null ? java.util.Map.of() : store.diagnostics(job))
                .withRecovery(repair, job == null ? null : store.initialAttemptState(job), repair == null ? java.util.Map.of() : store.repairDiagnostics(repair))
                .withAudioPolicy(NovelAudioService.POLICY_VERSION + "|" + ai.speechModel());
        return ruby == null ? snapshot : snapshot.withRuby(ruby);
    }
}
