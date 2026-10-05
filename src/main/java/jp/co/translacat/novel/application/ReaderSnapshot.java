package jp.co.translacat.novel.application;

import jp.co.translacat.novel.domain.EpisodeKey;
import jp.co.translacat.novel.domain.SourceEpisode;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public record ReaderSnapshot(String revision, String segmentationVersion, Title title, Pager pagerInfo,
                             List<Segment> segments, int totalSegments, long completedSegments,
                             String state, String jobId, long eventSequence, boolean durableComplete,
                             String policyVersion, String glossaryVersion, String traceId, String runTraceId,
                             String audioPolicyVersion, Progress progress, Map<String, Object> execution,
                             Map<String, Object> timings, Recovery recovery, String initialAttemptState) {
    public record Recovery(String repairId, String state, List<String> targetIds, List<String> committedIds,
                           String errorCode, String traceId, Map<String,Object> diagnostics) {}
    public record Progress(long sourceCodePoints, long completedSourceCodePoints, long contiguousPrefixCodePoints,
                           long totalTargetSegments, long completedTargetSegments) {}
    public record Title(String rawJa, String ja, String ko) {}
    public record Pager(String prevEpisodeId, String nextEpisodeId, String indexUrl,
                        String prevIdentifier, String nextIdentifier, String listIdentifier) {}
    public record Segment(String id, String paragraphId, int order, int paragraphIndex,
                          int startOffset, int endOffset, String rawJa, String plainJa,
                          String ko, String status, String errorCode, List<SourceEpisode.RubyToken> rubyTokens) {}

    public static ReaderSnapshot of(EpisodeKey key, SourceEpisode source, NovelRepository.Job job) {
        return of(key, source, job, jp.co.translacat.novel.domain.TranslationPolicy.GLOSSARY_VERSION);
    }

    public static ReaderSnapshot of(EpisodeKey key, SourceEpisode source, NovelRepository.Job job, String glossaryVersion) {
        List<Segment> segments = source.segments().stream().map(segment -> {
            String translated = jp.co.translacat.novel.domain.SourceText.isBlank(segment.plainJa()) ? "" : job == null ? null : job.results().get(segment.id());
            String error = job == null ? null : job.errors().get(segment.id());
            return new Segment(segment.id(), segment.paragraphId(), segment.order(), segment.paragraphIndex(),
                    segment.startOffset(), segment.endOffset(), segment.rawJa(), segment.plainJa(), translated,
                    translated != null ? "SUCCEEDED" : error != null ? "FAILED" : "PENDING", error, segment.rubyTokens());
        }).toList();
        return new ReaderSnapshot(source.revision(), source.segmentationVersion(),
                new Title(source.title(), source.title(), null),
                new Pager(source.prevEpisodeId(), source.nextEpisodeId(), "https://ncode.syosetu.com/" + key.novel() + "/",
                        source.prevEpisodeId(), source.nextEpisodeId(), key.novel()), segments, segments.size(),
                segments.stream().filter(segment -> segment.status().equals("SUCCEEDED")).count(),
                job == null ? "SOURCE_READY" : job.state(), job == null ? null : job.id(),
                job == null ? 0 : job.eventSequence(), job != null && job.state().equals("SUCCEEDED"),
                jp.co.translacat.novel.domain.TranslationPolicy.VERSION, glossaryVersion,
                null, null, NovelAudioService.POLICY_VERSION, progress(segments), Map.of(), Map.of(), null, job == null ? null : job.state());
    }

    private static Progress progress(List<Segment> segments) {
        long total = 0, completed = 0, prefix = 0, targets = 0, completedTargets = 0;
        boolean contiguous = true;
        for (Segment segment : segments) {
            int size = jp.co.translacat.novel.domain.SourceText.eligibleCodePoints(segment.plainJa());
            total += size;
            if (!jp.co.translacat.novel.domain.SourceText.isBlank(segment.plainJa())) targets++;
            if (segment.status().equals("SUCCEEDED")) {
                completed += size;
                if (!jp.co.translacat.novel.domain.SourceText.isBlank(segment.plainJa())) completedTargets++;
                if (contiguous) prefix += size;
            } else contiguous = false;
        }
        return new Progress(total, completed, prefix, targets, completedTargets);
    }

    public ReaderSnapshot withDiagnostics(Map<String, Object> diagnostics) {
        String run = diagnostics.get("traceId") instanceof String value ? value : null;
        return new ReaderSnapshot(revision, segmentationVersion, title, pagerInfo, segments, totalSegments,
                completedSegments, state, jobId, eventSequence, durableComplete, policyVersion, glossaryVersion,
                traceId, run, audioPolicyVersion, progress, diagnostics, timings, recovery, initialAttemptState);
    }

    public ReaderSnapshot observed() {
        RequestTrace current = RequestTrace.current();
        return new ReaderSnapshot(revision, segmentationVersion, title, pagerInfo, segments, totalSegments,
                completedSegments, state, jobId, eventSequence, durableComplete, policyVersion, glossaryVersion,
                current == null ? null : current.id(), runTraceId, audioPolicyVersion, progress, execution,
                current == null ? Map.of() : current.snapshot(), recovery, initialAttemptState);
    }

    public ReaderSnapshot withAudioPolicy(String value) {
        return new ReaderSnapshot(revision, segmentationVersion, title, pagerInfo, segments, totalSegments,
                completedSegments, state, jobId, eventSequence, durableComplete, policyVersion, glossaryVersion,
                traceId, runTraceId, value, progress, execution, timings, recovery, initialAttemptState);
    }

    public ReaderSnapshot withRecovery(NovelRepository.Repair repair, String initial, Map<String,Object> diagnostics) {
        Recovery value = repair == null ? null : new Recovery(repair.id(), repair.state(), repair.targetIds(),
                repair.targetIds().stream().filter(repair.results()::containsKey).toList(),
                repair.errors().values().stream().findFirst().orElse(null), repair.traceId(), diagnostics);
        return new ReaderSnapshot(revision, segmentationVersion, title, pagerInfo, segments, totalSegments,
                completedSegments, state, jobId, eventSequence, durableComplete, policyVersion, glossaryVersion,
                traceId, runTraceId, audioPolicyVersion, progress, execution, timings, value, initial);
    }
}
