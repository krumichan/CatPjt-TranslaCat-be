package jp.co.translacat.novel.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.translacat.novel.domain.NovelProblem;
import jp.co.translacat.novel.domain.SourceEpisode;
import jp.co.translacat.novel.domain.TranslationOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** 한 Provider 요청 안에서만 유효한 짧은 ID와 canonical ID의 불변 일대일 대응. */
public final class CompactTranslationBatch {
    private final List<SourceEpisode.Segment> wireSegments;
    private final Map<String, String> canonicalByWire;

    private CompactTranslationBatch(List<SourceEpisode.Segment> wireSegments, Map<String, String> canonicalByWire) {
        this.wireSegments = List.copyOf(wireSegments);
        this.canonicalByWire = Map.copyOf(canonicalByWire);
    }

    public static CompactTranslationBatch of(List<SourceEpisode.Segment> source) {
        if (source == null || source.isEmpty() || source.size() > 3000) {
            throw new NovelProblem("TRANSLATION_BATCH_INVALID", 500);
        }
        List<SourceEpisode.Segment> wire = new ArrayList<>();
        Map<String, String> ids = new HashMap<>();
        var canonicalIds = new HashSet<String>();

        // 원문 text가 같아도 서로 다른 위치의 canonical ID를 합치지 않는다.
        for (int index = 0; index < source.size(); index++) {
            SourceEpisode.Segment segment = source.get(index);
            if (segment == null || segment.id() == null || !canonicalIds.add(segment.id())) {
                throw new NovelProblem("TRANSLATION_BATCH_INVALID", 500);
            }
            String alias = "s" + index;
            ids.put(alias, segment.id());
            wire.add(new SourceEpisode.Segment(alias, segment.paragraphId(), segment.order(), segment.paragraphIndex(),
                    segment.startOffset(), segment.endOffset(), segment.rawJa(), segment.plainJa(), segment.rubyTokens()));
        }
        return new CompactTranslationBatch(wire, ids);
    }

    public List<SourceEpisode.Segment> wireSegments() { return wireSegments; }

    public Map<String, String> restore(JsonNode output) {
        return restore(output, TranslationOptions.ResponseShape.ARRAY_V2);
    }
    public Map<String, String> restore(JsonNode output, TranslationOptions.ResponseShape shape) {
        return restore(output, shape, TranslationOptions.ValidationPolicy.BASIC_V1);
    }
    public Map<String, String> restore(JsonNode output, TranslationOptions.ResponseShape shape, TranslationOptions.ValidationPolicy policy) {
        // 누락·중복·추가·잘못된 타입을 모두 검사한 후에만 canonical ID로 복원한다.
        Map<String, String> validated = shape != TranslationOptions.ResponseShape.ARRAY_V2
                ? new StrictTranslationValidator().validateKeyed(output, wireSegments, policy) : new StrictTranslationValidator().validate(output, wireSegments, policy);
        Map<String, String> restored = new java.util.LinkedHashMap<>();
        wireSegments.forEach(segment -> restored.put(canonicalByWire.get(segment.id()), validated.get(segment.id())));
        return java.util.Collections.unmodifiableMap(restored);
    }
}
