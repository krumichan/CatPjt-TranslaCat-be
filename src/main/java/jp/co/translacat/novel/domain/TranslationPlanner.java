package jp.co.translacat.novel.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 문자열을 삭제/요약하지 않고 기존 canonical 문장들의 경계에서만 계획한다. */
public final class TranslationPlanner {
    public record Chunk(int index, List<SourceEpisode.Segment> targets, List<SourceEpisode.Segment> context,
                        int sourceCodePoints, int estimatedInputTokens, int estimatedOutputTokens,
                        java.util.Map<String, Object> contextSelection) {}
    public record Plan(int requestedN, int actualN, int requestedC, int effectiveC, String strategy,
                       String contextPolicy, String responseShape, String segmentationVersion, String validationPolicy, String annotationPolicy,
                       String plannerVersion, String policyFingerprint,
                       List<String> adjustmentReasons, int sourceCodePoints, List<Chunk> chunks,
                       String lengthPolicy, String lengthPolicyVersion, String lengthBand) {}

    public Plan plan(SourceEpisode source, TranslationOptions options) { return plan(source, options, false); }

    public Plan plan(SourceEpisode source, TranslationOptions options, boolean hasGlossary) {
        return planResolved(source, TranslationLengthPolicy.resolve(source, options), hasGlossary);
    }

    public Plan planResolved(SourceEpisode source, TranslationLengthPolicy.Resolution selection, boolean hasGlossary) {
        if (!source.revision().equals(selection.sourceRevision())) throw new NovelProblem("SOURCE_REVISION_CHANGED", 409);
        TranslationOptions options = selection.options();
        if (!source.segmentationVersion().equals(options.segmentationPolicy().version())) {
            throw new NovelProblem("SOURCE_SEGMENTATION_POLICY_MISMATCH", 409);
        }
        List<SourceEpisode.Segment> targets = source.segments().stream().filter(s -> !jp.co.translacat.novel.domain.SourceText.isBlank(s.plainJa())).toList();
        List<String> reasons = new ArrayList<>();
        List<List<SourceEpisode.Segment>> groups;
        if (options.strategy() == TranslationOptions.Strategy.LEGACY) {
            groups = new TranslationPolicy().chunks(targets);
            reasons.add("LEGACY_AUTOMATIC_TOKEN_AND_ITEM_LIMIT");
        } else {
            int count = Math.min(options.requestedChunks(), targets.size());
            if (count < options.requestedChunks()) reasons.add("FEWER_TARGET_SEGMENTS");
            groups = partition(targets, count, options.strategy());
            List<List<SourceEpisode.Segment>> safe = new ArrayList<>();
            int tokenCap = (int) (options.maxOutputTokens() * .9) - 128;
            for (List<SourceEpisode.Segment> group : groups) {
                List<SourceEpisode.Segment> part = new ArrayList<>();
                int tokens = 0;
                for (SourceEpisode.Segment segment : group) {
                    int weight = outputEstimate(segment);
                    if (weight > tokenCap) throw new NovelProblem("SEGMENT_OUTPUT_BUDGET_EXCEEDED", 422);
                    if (!part.isEmpty() && tokens + weight > tokenCap) {
                        safe.add(List.copyOf(part));
                        part.clear();
                        tokens = 0;
                    }
                    part.add(segment);
                    tokens += weight;
                }
                if (!part.isEmpty()) safe.add(List.copyOf(part));
            }
            if (safe.size() > groups.size()) reasons.add("OUTPUT_TOKEN_SAFETY");
            groups = safe;
        }
        if (groups.size() > 128) throw new NovelProblem("TRANSLATION_CHUNK_LIMIT", 413);
        List<Chunk> chunks = new ArrayList<>();
        for (List<SourceEpisode.Segment> group : groups) {
            var selected = options.context() == TranslationOptions.Context.CONTIGUOUS_WIDE_V2
                    ? contiguousContext(source, group)
                    : new SelectedContext(context(source, group, options.context(), hasGlossary), java.util.Map.of());
            List<SourceEpisode.Segment> context = selected.units();
            int chars = group.stream().mapToInt(TranslationPlanner::codePoints).sum();
            int contextChars = context.stream().mapToInt(TranslationPlanner::codePoints).sum();
            chunks.add(new Chunk(chunks.size(), group, context, chars,
                    (int) Math.ceil((chars + contextChars) * 1.5) + group.size() * 16 + 160,
                    group.stream().mapToInt(TranslationPlanner::outputEstimate).sum(), selected.metadata()));
        }
        // 계획기 버그도 외부 호출 전에 누락·중복을 차단한다.
        List<String> before = targets.stream().map(SourceEpisode.Segment::id).toList();
        List<String> after = chunks.stream().flatMap(c -> c.targets().stream()).map(SourceEpisode.Segment::id).toList();
        if (!before.equals(after) || new HashSet<>(after).size() != after.size()) {
            throw new NovelProblem("TRANSLATION_PLAN_INVALID", 500);
        }
        return new Plan(options.requestedChunks(), chunks.size(), options.requestedConcurrency(),
                Math.min(options.requestedConcurrency(), chunks.size()), options.strategy().name(),
                options.context().name(), options.responseShape().name(), source.segmentationVersion(),
                options.validationPolicy().name(), options.annotationPolicy().name(), TranslationOptions.VERSION, options.fingerprint(), List.copyOf(reasons),
                selection.sourceCodePoints(), List.copyOf(chunks), options.lengthPolicy().name(), selection.policyVersion(), selection.band());
    }

    private List<List<SourceEpisode.Segment>> partition(List<SourceEpisode.Segment> targets, int count,
                                                       TranslationOptions.Strategy strategy) {
        List<List<SourceEpisode.Segment>> groups = new ArrayList<>();
        if (targets.isEmpty()) return groups;
        long[] prefix = new long[targets.size() + 1];
        int[] dialogueDepth = new int[targets.size() + 1];
        for (int i = 0; i < targets.size(); i++) {
            prefix[i + 1] = prefix[i] + outputEstimate(targets.get(i));
            int depth = dialogueDepth[i];
            for (int cp : targets.get(i).plainJa().codePoints().toArray()) {
                if (cp == '「' || cp == '『') depth++;
                if (cp == '」' || cp == '』') depth = Math.max(0, depth - 1);
            }
            dialogueDepth[i + 1] = depth;
        }
        int start = 0;
        for (int group = 0; group < count - 1; group++) {
            double ideal = prefix[start] + (prefix[targets.size()] - prefix[start]) / (double) (count - group);
            double size = (prefix[targets.size()] - prefix[start]) / (double) (count - group);
            int best = start + 1;
            double bestScore = Double.POSITIVE_INFINITY;
            int max = targets.size() - (count - group - 1);
            for (int cut = start + 1; cut <= max; cut++) {
                double score = Math.abs(prefix[cut] - ideal);
                if (strategy == TranslationOptions.Strategy.SEMANTIC) {
                    if (targets.get(cut - 1).paragraphIndex() == targets.get(cut).paragraphIndex()) score += size * .45;
                    if (dialogueDepth[cut] > 0) score += size;
                }
                if (score < bestScore) { best = cut; bestScore = score; }
            }
            groups.add(List.copyOf(targets.subList(start, best)));
            start = best;
        }
        groups.add(List.copyOf(targets.subList(start, targets.size())));
        return groups;
    }

    private List<SourceEpisode.Segment> context(SourceEpisode source, List<SourceEpisode.Segment> group,
                                               TranslationOptions.Context policy, boolean hasGlossary) {
        int first = group.getFirst().order(), last = group.getLast().order();
        if (policy == TranslationOptions.Context.CURRENT) {
            List<SourceEpisode.Segment> current = new ArrayList<>();
            if (!hasGlossary) {
                if (first > 0) current.add(source.segments().get(first - 1));
                if (last + 1 < source.segments().size()) current.add(source.segments().get(last + 1));
            } else {
                int chars = 0;
                for (int i = Math.max(0, first - 12); i < Math.min(source.segments().size(), last + 4); i++) {
                    if (i >= first && i <= last) continue;
                    var candidate = source.segments().get(i);
                    if (chars + candidate.plainJa().length() <= 2000) { current.add(candidate); chars += candidate.plainJa().length(); }
                }
            }
            return List.copyOf(current);
        }
        int before = policy == TranslationOptions.Context.SMALL ? 2 : policy == TranslationOptions.Context.WIDE || hasGlossary ? 12 : 1;
        int after = policy == TranslationOptions.Context.SMALL ? 2 : policy == TranslationOptions.Context.WIDE || hasGlossary ? 3 : 1;
        int maxChars = policy == TranslationOptions.Context.SMALL ? 600 : 4000;
        List<SourceEpisode.Segment> candidates = new ArrayList<>();
        if (policy == TranslationOptions.Context.WIDE) {
            int firstParagraph = group.getFirst().paragraphIndex(), lastParagraph = group.getLast().paragraphIndex();
            for (var candidate : source.segments()) {
                if ((candidate.order() < first || candidate.order() > last)
                        && candidate.paragraphIndex() >= firstParagraph - 1 && candidate.paragraphIndex() <= lastParagraph + 1) {
                    candidates.add(candidate);
                }
            }
        } else {
            for (int index = Math.max(0, first - before); index < Math.min(source.segments().size(), last + after + 1); index++) {
                if (index < first || index > last) candidates.add(source.segments().get(index));
            }
        }
        // 가까운 완전한 문장을 우선한다. 문맥을 잘라서 새로운 번역 target으로 만들지 않는다.
        if (policy != TranslationOptions.Context.CURRENT) candidates.sort(Comparator.comparingInt(s -> Math.min(Math.abs(s.order() - first), Math.abs(s.order() - last))));
        List<SourceEpisode.Segment> result = new ArrayList<>();
        int chars = 0;
        for (SourceEpisode.Segment candidate : candidates) {
            int count = codePoints(candidate);
            if (chars + count <= maxChars) { result.add(candidate); chars += count; }
        }
        result.sort(Comparator.comparingInt(SourceEpisode.Segment::order));
        return List.copyOf(result);
    }

    private record SelectedContext(List<SourceEpisode.Segment> units, java.util.Map<String, Object> metadata) {}
    private record DirectionWindow(List<SourceEpisode.Segment> units, int codePoints, Integer stoppedAtOrder) {}

    private SelectedContext contiguousContext(SourceEpisode source, List<SourceEpisode.Segment> targets) {
        int first = targets.getFirst().order(), last = targets.getLast().order();
        // 이전/이후 예산을 따로 적용한다. 가까운 단위가 안 들어가면 먼 문장으로 건너뛰지 않는다.
        var before = directionWindow(source, first - 1, -1, 3000);
        var after = directionWindow(source, last + 1, 1, 1000);
        var units = new ArrayList<>(before.units());
        units.addAll(after.units());
        var metadata = new java.util.LinkedHashMap<String, Object>();
        metadata.put("policy", TranslationOptions.Context.CONTIGUOUS_WIDE_V2.name());
        metadata.put("beforeCapCodePoints", 3000); metadata.put("afterCapCodePoints", 1000);
        metadata.put("totalCapCodePoints", 4000);
        // 문맥의 빈/공백 단위도 보존하며 원래 코드 포인트 수를 예산에 포함한다.
        metadata.put("blankPolicy", "PRESERVE_AND_COUNT_RAW_CODE_POINTS");
        windowMetadata(metadata, "before", before);
        windowMetadata(metadata, "after", after);
        return new SelectedContext(List.copyOf(units), java.util.Collections.unmodifiableMap(metadata));
    }

    private DirectionWindow directionWindow(SourceEpisode source, int start, int step, int cap) {
        var units = new ArrayList<SourceEpisode.Segment>();
        int used = 0;
        Integer stoppedAt = null;
        for (int index = start; index >= 0 && index < source.segments().size(); index += step) {
            var candidate = source.segments().get(index);
            int weight = codePoints(candidate);
            if (weight > cap - used) { stoppedAt = candidate.order(); break; }
            units.add(candidate); used += weight;
        }
        // 수집은 가까운 순서였어도 전송은 항상 원문 순서다. 문자열이나 루비를 자르지 않는다.
        if (step < 0) java.util.Collections.reverse(units);
        return new DirectionWindow(List.copyOf(units), used, stoppedAt);
    }

    private void windowMetadata(java.util.Map<String, Object> metadata, String direction, DirectionWindow window) {
        metadata.put(direction + "CodePoints", window.codePoints());
        metadata.put(direction + "Units", window.units().size());
        metadata.put(direction + "FirstOrder", window.units().isEmpty() ? null : window.units().getFirst().order());
        metadata.put(direction + "LastOrder", window.units().isEmpty() ? null : window.units().getLast().order());
        metadata.put(direction + "StoppedAtOrder", window.stoppedAtOrder());
        metadata.put(direction + "StopReason", window.stoppedAtOrder() == null ? "SOURCE_EDGE" : "NEAREST_UNIT_EXCEEDS_REMAINING_BUDGET");
    }

    public static int codePoints(SourceEpisode.Segment segment) {
        return segment.plainJa().codePointCount(0, segment.plainJa().length());
    }
    private static int outputEstimate(SourceEpisode.Segment segment) { return codePoints(segment) * 2 + 16; }
}
