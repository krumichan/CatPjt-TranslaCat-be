package jp.co.translacat.novel.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.translacat.novel.domain.NovelProblem;
import jp.co.translacat.novel.domain.SourceEpisode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class StrictTranslationValidator {
    public Map<String, String> validate(JsonNode output, List<SourceEpisode.Segment> expected,
                                       jp.co.translacat.novel.domain.TranslationOptions.ValidationPolicy policy) {
        var result = validate(output, expected);
        jp.co.translacat.novel.domain.TranslationContentGuard.validate(expected, result, policy);
        return result;
    }
    public Map<String, String> validateKeyed(JsonNode output, List<SourceEpisode.Segment> expected,
                                            jp.co.translacat.novel.domain.TranslationOptions.ValidationPolicy policy) {
        var result = validateKeyed(output, expected);
        jp.co.translacat.novel.domain.TranslationContentGuard.validate(expected, result, policy);
        return result;
    }
    public Map<String, String> validate(JsonNode output, List<SourceEpisode.Segment> expected) {
        if (output == null || !output.isObject() || output.size() != 1 || !output.path("items").isArray()
                || output.path("items").size() != expected.size()) {
            throw invalid();
        }
        Set<String> allowed = new HashSet<>(expected.stream().map(SourceEpisode.Segment::id).toList());
        Map<String, String> result = new HashMap<>();

        // ID 대응을 완전히 확인한 청크만 저장한다. 모델 배열 순서는 화면 정렬에 사용하지 않는다.
        for (JsonNode item : output.path("items")) {
            if (!item.isObject() || item.size() != 2 || !item.path("id").isTextual() || !item.path("text").isTextual()) {
                throw invalid();
            }
            String id = item.path("id").textValue();
            String text = item.path("text").textValue();
            if (!allowed.contains(id) || result.containsKey(id) || jp.co.translacat.novel.domain.SourceText.isBlank(text) || text.length() > 12_000
                    || text.indexOf('\0') >= 0) {
                throw invalid();
            }
            result.put(id, text);
        }
        if (!result.keySet().equals(allowed)) {
            throw invalid();
        }
        return Map.copyOf(result);
    }

    private NovelProblem invalid() {
        return new NovelProblem("TRANSLATION_SCHEMA_INVALID", 502, true, 0);
    }

    public Map<String, String> validateKeyed(JsonNode output, List<SourceEpisode.Segment> expected) {
        if (output == null || !output.isObject() || output.size() != expected.size()) throw invalid();
        Set<String> allowed = new HashSet<>(expected.stream().map(SourceEpisode.Segment::id).toList());
        Set<String> returned = new HashSet<>(); output.fieldNames().forEachRemaining(returned::add);
        if (!returned.equals(allowed)) throw invalid();
        Map<String, String> result = new java.util.LinkedHashMap<>();
        for (SourceEpisode.Segment segment : expected) {
            JsonNode item = output.get(segment.id());
            if (!item.isTextual()) throw invalid();
            String text = item.textValue();
            if (jp.co.translacat.novel.domain.SourceText.isBlank(text) || text.length() > 12000 || text.indexOf('\0') >= 0) throw invalid();
            result.put(segment.id(), text);
        }
        return java.util.Collections.unmodifiableMap(result);
    }
}
