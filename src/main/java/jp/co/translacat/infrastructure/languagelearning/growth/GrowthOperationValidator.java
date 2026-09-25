package jp.co.translacat.infrastructure.languagelearning.growth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.languagelearning.growth.model.GrowthOperation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Core 커밋 전에 LL 수신 계약을 검사한다. 잘못된 명령을 outbox에 확정하지 않는다.
 */
public final class GrowthOperationValidator {
    private static final Set<String> SIGNALS =
            Set.of("GRAMMAR_WEAKNESS", "ERROR_PATTERN", "STRENGTH", "WEAKNESS", "RECOMMENDED_FOCUS",
                    "VOCABULARY_CANDIDATE");
    private static final Set<String> METRICS =
            Set.of("MEANING", "GRAMMAR", "VOCABULARY", "NATURALNESS", "EXPRESSIVENESS", "FLUENCY", "PRONUNCIATION",
                    "INTERACTION");

    private GrowthOperationValidator() {
    }

    public static void validate(GrowthOperation operation, ObjectMapper mapper) {
        JsonNode p = mapper.valueToTree(operation.payload());
        switch (operation.kind()) {
            case "LEARNING_PREPARED" -> {
                fields(p, "learningDate");
                date(p, "learningDate");
            }
            case "WRITING_SCORED" -> {
                fields(p, "learningDate", "difficulty", "scores", "signals", "canonicalKeys");
                date(p, "learningDate");
                choice(p, "difficulty", Set.of("REVIEW", "NORMAL", "CHALLENGE"));
                JsonNode scores = array(p, "scores", 5);
                require(scores.size() == 5);
                scores.forEach(n -> number(n, 100));
                JsonNode signals = p.get("signals");
                require(signals != null && signals.isObject());
                signals.fields().forEachRemaining(entry -> {
                    require(SIGNALS.contains(entry.getKey()));
                    strings(entry.getValue(), 100, 4096);
                });
                strings(p.get("canonicalKeys"), 100, 200);
            }
            case "SIGNALS_TOUCHED" -> {
                fields(p, "type", "values");
                choice(p, "type", SIGNALS);
                strings(p.get("values"), 100, 4096);
            }
            case "KEYWORDS_SELECTED" -> {
                fields(p, "learningDate", "canonicalKeys");
                date(p, "learningDate");
                strings(p.get("canonicalKeys"), 100, 200);
            }
            case "ACTIVITY_RECORDED" -> {
                fields(p, "activity", "metrics");
                activity(p.get("activity"), mapper);
                JsonNode metrics = p.get("metrics");
                require(metrics == null || metrics.isNull() || (metrics.isArray() && metrics.isEmpty()));
            }
            case "SPEAKING_SCORED" -> {
                fields(p, "resultKind", "activity", "metrics", "formal", "activityWeight", "evidence");
                choice(p, "resultKind", Set.of("SCORED_EVALUATION"));
                JsonNode a = p.get("activity");
                activity(a, mapper);
                choice(a, "source", Set.of("SPEAKING"));
                JsonNode formal = p.get("formal");
                require(formal != null && formal.isBoolean());
                choice(a, "status", Set.of(formal.booleanValue() ? "EVALUATED" : "INSUFFICIENT_EVIDENCE"));
                number(p.get("activityWeight"), 1);
                Set<String> types = new HashSet<>();
                for (JsonNode m : array(p, "metrics", 10)) {
                    fields(m, "metricType", "state", "score", "confidence", "notEvaluableReason");
                    choice(m, "metricType", METRICS);
                    require(types.add(m.get("metricType").textValue()));
                    choice(m, "state", Set.of("EVALUATED", "NOT_EVALUABLE"));
                    optionalNumber(m, "score", 100);
                    optionalNumber(m, "confidence", 1);
                    optionalText(m, "notEvaluableReason", 1000);
                }
                for (JsonNode e : array(p, "evidence", 100)) {
                    fields(e, "metricType", "patternKey", "direction", "confidence", "recommendedFocus");
                    if (present(e, "metricType")) choice(e, "metricType", METRICS);
                    text(e.get("patternKey"), 4096);
                    optionalText(e, "direction", 30);
                    number(e.get("confidence"), 1);
                    optionalText(e, "recommendedFocus", 1000);
                }
            }
            default -> throw invalid();
        }
    }

    private static void activity(JsonNode a, ObjectMapper mapper) {
        fields(a, "source", "referenceId", "learningDate", "title", "durationSeconds", "status", "overallScore",
                "evaluationConfidence", "startedAt", "completedAt", "metadataJson");
        choice(a, "source", Set.of("WRITING", "SPEAKING", "LISTENING", "READING", "VOCABULARY"));
        text(a.get("referenceId"), 100);
        text(a.get("title"), 300);
        date(a, "learningDate");
        JsonNode duration = a.get("durationSeconds");
        require(duration != null
                && duration.isIntegralNumber()
                && duration.canConvertToLong()
                && duration.longValue() >= 0);
        String status = text(a.get("status"), 40);
        require(Set.of("COMPLETED", "EVALUATING", "EVALUATED", "EVALUATION_FAILED", "INSUFFICIENT_EVIDENCE")
                .contains(status));
        optionalNumber(a, "overallScore", 100);
        optionalNumber(a, "evaluationConfidence", 1);
        require(!status.equals("EVALUATED") || present(a, "overallScore") && present(a, "evaluationConfidence"));
        require(!status.equals("INSUFFICIENT_EVIDENCE") || !present(a, "overallScore"));
        time(a, "startedAt");
        if (present(a, "completedAt")) time(a, "completedAt");
        try {
            JsonNode metadata = mapper.readTree(text(a.get("metadataJson"), 32768));
            require(metadata != null && metadata.isObject());
            JsonNode kind = metadata.get("resultKind");
            require(kind == null || !kind.asText().equals("SESSION_COACHING") || status.equals("COMPLETED") && !present(
                    a, "overallScore") && !present(a, "evaluationConfidence"));
        } catch (java.io.IOException error) {
            throw invalid();
        }
    }

    private static void fields(JsonNode n, String... names) {
        require(n != null && n.isObject());
        Set<String> allowed = Set.of(names);
        n.fieldNames().forEachRemaining(name -> require(allowed.contains(name)));
    }

    private static boolean present(JsonNode n, String key) {
        return n.hasNonNull(key);
    }

    private static String text(JsonNode n, int max) {
        require(n != null && n.isTextual() && !n.textValue().isEmpty() && n.textValue().length() <= max);
        return n.textValue();
    }

    private static void optionalText(JsonNode n, String key, int max) {
        if (present(n, key)) text(n.get(key), max);
    }

    private static void choice(JsonNode n, String key, Set<String> allowed) {
        require(allowed.contains(text(n.get(key), 40)));
    }

    private static void date(JsonNode n, String key) {
        LocalDate.parse(text(n.get(key), 10));
    }

    private static void time(JsonNode n, String key) {
        LocalDateTime.parse(text(n.get(key), 40));
    }

    private static void number(JsonNode n, double max) {
        require(n != null
                && n.isNumber()
                && Double.isFinite(n.doubleValue())
                && n.doubleValue() >= 0
                && n.doubleValue() <= max);
    }

    private static void optionalNumber(JsonNode n, String key, double max) {
        if (present(n, key)) number(n.get(key), max);
    }

    private static JsonNode array(JsonNode n, String key, int max) {
        JsonNode a = n.get(key);
        require(a != null && a.isArray() && a.size() <= max);
        return a;
    }

    private static void strings(JsonNode a, int limit, int length) {
        require(a != null && a.isArray() && a.size() <= limit);
        a.forEach(n -> text(n, length));
    }

    private static void require(boolean condition) {
        if (!condition) throw invalid();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("성장 명령이 LL 계약과 일치하지 않습니다.");
    }
}
