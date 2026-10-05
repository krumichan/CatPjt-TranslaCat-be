package jp.co.translacat.infrastructure.languagelearning.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;

import java.util.HashSet;
import java.util.Set;

/** 새 비점수 계획의 수량·완료·비공개 답안 경계를 중계 전에 검증한다. */
final class WritingPlanContract {
    static final String POLICY = "curated-writing-variable-n-v1";

    private WritingPlanContract() { }

    static JsonNode config(JsonNode value) {
        requirePolicy(value);
        int maximum = integer(value, "maxTargetItemCount");
        int minimum = integer(value, "minTargetItemCount");
        int initial = integer(value, "defaultTargetItemCount");
        if (minimum < 1 || maximum < minimum || initial < minimum || initial > maximum
                || integer(value, "workerBatchSize") < 1 || integer(value, "pageSize") < 1) fail();
        return value;
    }

    static JsonNode preview(JsonNode value) {
        requireReferencePolicy(value);
        counts(value);
        if (value.has("items") || value.has("referenceAnswers")) fail();
        return value;
    }

    static JsonNode plan(JsonNode value) {
        requireReferencePolicy(value);
        int target = integer(value, "targetItemCount");
        int acquired = counts(value);
        int submitted = integer(value, "submittedItemCount");
        int feedback = integer(value, "feedbackCompletedItemCount");
        if (submitted < 0 || submitted > acquired || feedback < 0 || feedback > submitted
                || integer(value, "sentenceCount") != target
                || integer(value, "generatedItemCount") != acquired
                || integer(value, "planRevision") < 1
                || !value.path("items").isArray() || value.path("items").size() != acquired) fail();

        // 답안을 제출한 개별 문항에만 참고 답안이 있어야 한다. 수량 완료와 피드백 완료는 별개다.
        Set<String> ids = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        int answered = 0;
        int feedbackCompleted = 0;
        String feedbackStatus = value.path("personalizedFeedbackStatus").asText();
        if (!Set.of("ACTIVE_QA", "DISABLED_PENDING_QUALITY_GATE").contains(feedbackStatus)) fail();
        for (JsonNode item : value.path("items")) {
            int order = integer(item, "order");
            if (order < 1 || order > target || !orders.add(order)
                    || !item.hasNonNull("itemId") || !ids.add(item.path("itemId").asText())
                    || !item.path("answered").isBoolean() || !item.path("attempts").isArray()) fail();
            boolean hasAnswer = item.path("answered").booleanValue();
            if (hasAnswer) answered++;
            if (hasAnswer != !item.path("attempts").isEmpty()) fail();
            if (!hasAnswer && (item.has("reference") || item.has("referenceAnswers") || item.has("checklist"))) fail();
            if (item.has("evaluationKey") || item.has("privateJson")) fail();
            for (JsonNode attempt : item.path("attempts")) {
                if (!"REFERENCE_ONLY".equals(attempt.path("resultPolicy").asText())
                        || !attempt.path("evaluation").isNull()) fail();
                JsonNode state = attempt.path("feedback");
                if ("ACTIVE_QA".equals(feedbackStatus)) {
                    if (!state.isObject() || !Set.of("PENDING", "PROCESSING", "SUCCEEDED", "FAILED", "UNCERTAIN")
                            .contains(state.path("status").asText()) || !state.path("canRetry").isBoolean()) fail();
                    boolean succeeded = "SUCCEEDED".equals(state.path("status").asText());
                    if (succeeded != state.path("result").isObject()) fail();
                    if (state.path("canRetry").asBoolean() && !Set.of("FAILED", "PROCESSING")
                            .contains(state.path("status").asText())) fail();
                    if (succeeded) {
                        JsonNode result = state.path("result");
                        if (!"curated-writing-feedback-v1".equals(result.path("policyVersion").asText())
                                || !"REFERENCE_ONLY".equals(result.path("resultPolicy").asText())
                                || !result.path("observations").isArray()
                                || !result.path("necessaryCorrections").isArray()
                                || !result.path("optionalAlternatives").isArray()) fail();
                        if (result.path("observations").size() != item.path("reference").path("checklist").size()) fail();
                        for (JsonNode correction : result.path("necessaryCorrections")) {
                            if (!attempt.path("answer").asText().contains(correction.path("original").asText())) fail();
                        }
                    }
                } else if (!state.isMissingNode()) fail();
            }
            if ("ACTIVE_QA".equals(feedbackStatus) && !item.path("attempts").isEmpty()
                    && "SUCCEEDED".equals(item.path("attempts").get(item.path("attempts").size() - 1)
                    .path("feedback").path("status").asText())) feedbackCompleted++;
        }
        if (answered != submitted || feedbackCompleted != feedback) fail();
        String status = value.path("status").asText();
        if (!Set.of("READY", "PARTIAL", "COMPLETED", "BLOCKED").contains(status)) fail();
        if ("COMPLETED".equals(status) && (acquired != target || submitted != target)) fail();
        if ("READY".equals(status) && acquired != target) fail();
        return value;
    }

    private static int counts(JsonNode value) {
        int target = integer(value, "targetItemCount");
        int acquired = integer(value, "acquiredItemCount");
        int remaining = integer(value, "remainingItemCount");
        if (target < 1 || acquired < 0 || acquired > target || remaining != target - acquired) fail();
        return acquired;
    }

    private static int integer(JsonNode value, String key) {
        JsonNode number = value.path(key);
        if (!number.isIntegralNumber() || !number.canConvertToInt()) fail();
        return number.intValue();
    }

    private static void requirePolicy(JsonNode value) {
        if (value == null || !value.isObject() || !POLICY.equals(value.path("policyVersion").asText())) fail();
    }

    private static void requireReferencePolicy(JsonNode value) {
        requirePolicy(value);
        if (!"REFERENCE_ONLY".equals(value.path("resultPolicy").asText())) fail();
    }

    private static void fail() {
        throw new LanguageLearningServiceException(HttpStatus.BAD_GATEWAY, "LL_WRITING_CONTRACT_ERROR",
                "Writing 계획의 수량 또는 응답 계약이 올바르지 않습니다.");
    }
}
