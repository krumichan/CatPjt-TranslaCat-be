package jp.co.translacat.novel.application;

import jp.co.translacat.novel.domain.EpisodeKey;
import jp.co.translacat.novel.domain.SourceEpisode;

import java.util.Map;
import java.util.Optional;

/** 소설 저장 port. JDBC/DB transaction 구현은 infrastructure가 소유한다. */
public interface NovelRepository {
    record StoredSource(SourceEpisode source, long fetchedAt) {}
    record Glossary(String version, Map<String, String> terms) {}
    record Job(String cacheKey, String id, String sourceKey, String revision, long actorId,
               String state, Map<String, String> results, Map<String, String> errors,
               String owner, long fence, long leaseUntil, long eventSequence,
               int providerCalls, long inputTokens, long outputTokens) {}
    record Repair(String id, String cacheKey, String state, java.util.List<String> targetIds,
                  Map<String,String> results, Map<String,String> errors, String owner, long fence,
                  long leaseUntil, String traceId) {}
    record RepairClaim(Repair repair, boolean claimed) {}

    Optional<StoredSource> current(EpisodeKey key);
    SourceEpisode revision(EpisodeKey key, String revision);
    SourceEpisode saveSource(EpisodeKey key, SourceEpisode source, long fetchedAt);
    Job create(EpisodeKey key, SourceEpisode source, long actorId, String cacheKey, String requestKey);
    Optional<Job> byCache(String cacheKey);
    Job byId(EpisodeKey key, String id, long actorId);
    Optional<Job> claim(String cacheKey, boolean retry, long leaseMillis);
    boolean reserveCall(Job job);
    boolean merge(Job owner, Map<String, String> results, Map<String, String> errors, long inputTokens, long outputTokens);
    void finish(Job owner, SourceEpisode source);
    default void finish(Job owner, SourceEpisode source, boolean providerVerified) {
        if (!providerVerified) throw new IllegalStateException("Repository must support verified progressive completion");
        finish(owner, source);
    }
    void releaseRejected(Job owner);
    Job expireAbandoned(Job job, SourceEpisode source);
    Glossary glossary(EpisodeKey key, long actorId);
    Glossary saveGlossary(EpisodeKey key, long actorId, String expectedVersion, Map<String, String> terms);
    Map<String, Object> diagnostics(Job job);
    boolean saveDiagnostics(Job owner, Map<String, Object> diagnostics);
    Job cancel(EpisodeKey key, String jobId, long actorId, SourceEpisode source);
    default RepairClaim claimRepair(Job job, SourceEpisode source, java.util.List<String> ids, String requestKey,
                                     String traceId, long leaseMillis) { throw new UnsupportedOperationException(); }
    default Optional<Repair> latestRepair(Job job) { return Optional.empty(); }
    default boolean reserveRepairCall(Repair owner) { return false; }
    default boolean mergeRepair(Repair owner, Map<String,String> values, Map<String,String> errors, long input, long output) { return false; }
    default void finishRepair(Repair owner, SourceEpisode source) { throw new UnsupportedOperationException(); }
    default String initialAttemptState(Job job) { return job.state(); }
    default Map<String,Object> repairDiagnostics(Repair repair) { return Map.of(); }
    default void saveRepairDiagnostics(Repair repair, Map<String,Object> diagnostics) {}
}
