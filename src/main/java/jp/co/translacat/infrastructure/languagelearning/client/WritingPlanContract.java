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
            if (item.has("taskRequirements")) {
                if (!item.path("taskRequirements").isArray()) fail();
                for (JsonNode requirement : item.path("taskRequirements")) {
                    if (!requirement.isTextual() || requirement.asText().isBlank()) fail();
                }
            }
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
                        if (!Set.of("curated-writing-feedback-v1", "curated-writing-feedback-v2", "curated-writing-feedback-v3", "curated-writing-feedback-v4", "curated-writing-feedback-v5", "curated-writing-feedback-v6", "curated-writing-feedback-v7")
                                .contains(result.path("policyVersion").asText())
                                || !"REFERENCE_ONLY".equals(result.path("resultPolicy").asText())
                                || !result.path("observations").isArray()
                                || !result.path("necessaryCorrections").isArray()
                                || !result.path("optionalAlternatives").isArray()) fail();
                        if (result.path("observations").size() != item.path("reference").path("checklist").size()) fail();
                        for (JsonNode correction : result.path("necessaryCorrections")) {
                            if (!attempt.path("answer").asText().contains(correction.path("original").asText())) fail();
                        }
                        // 저장된 v1 결과는 바꾸지 않고 새 계약의 누락 근거와 가상 예시 분류를 확인한다.
                        if (Set.of("curated-writing-feedback-v2", "curated-writing-feedback-v3", "curated-writing-feedback-v4", "curated-writing-feedback-v5", "curated-writing-feedback-v6", "curated-writing-feedback-v7").contains(result.path("policyVersion").asText())) {
                            validateGroundedFeedback(result, attempt.path("answer").asText(), item.path("taskRequirements"));
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

    private static void validateGroundedFeedback(JsonNode result, String answer, JsonNode requirements) {
        java.util.Map<String, JsonNode> observations = new java.util.HashMap<>();
        boolean typed = Set.of("curated-writing-feedback-v4", "curated-writing-feedback-v5", "curated-writing-feedback-v6", "curated-writing-feedback-v7").contains(result.path("policyVersion").asText());
        boolean structured = Set.of("curated-writing-feedback-v6", "curated-writing-feedback-v7").contains(result.path("policyVersion").asText());
        for (JsonNode observation : result.path("observations")) {
            String id = observation.path("requirementId").asText();
            String evidence = observation.path("evidenceKind").asText();
            if (id.isBlank() || observations.put(id, observation) != null
                    || !observation.path("segmentIds").isArray() || !observation.path("answerQuotes").isArray()) fail();
            if ("MISSING_REQUIREMENT".equals(evidence)) {
                if (!"UNMET".equals(observation.path("status").asText())
                        || !observation.path("segmentIds").isEmpty() || !observation.path("answerQuotes").isEmpty()) fail();
            } else if (!"LEARNER_TEXT".equals(evidence) || observation.path("segmentIds").isEmpty()
                    || observation.path("segmentIds").size() != observation.path("answerQuotes").size()) fail();
            for (JsonNode quote : observation.path("answerQuotes")) {
                if (!quote.isTextual() || quote.asText().isEmpty() || !answer.contains(quote.asText())) fail();
            }
            if (structured) validateRequirementParts(observation, answer, requirements);
            else if (typed) validateRelations(observation);
        }

        // 개인 사실을 채운 문장을 필수 교정으로 중계하지 않는다. 누락 설명은 observations에 남는다.
        for (JsonNode correction : result.path("necessaryCorrections")) {
            JsonNode observation = observations.get(correction.path("requirementId").asText());
            if (observation == null || !"LEARNER_TEXT".equals(observation.path("evidenceKind").asText())) fail();
            boolean found = false;
            for (JsonNode segment : observation.path("segmentIds")) {
                if (segment.asText().equals(correction.path("segmentId").asText())) found = true;
            }
            if (!found) fail();
            if (structured) {
                validatePatch(correction);
                boolean grounded = false;
                for (JsonNode part : observation.path("requirementParts")) {
                    // 사실 교정은 같은 문장의 다른 정상 사실이 아니라 충돌한 사실의 근거만 바꿀 수 있다.
                    if ("TASK_FACT_CONFLICT".equals(correction.path("issueType").asText())) {
                        boolean matchingFact = false;
                        for (JsonNode fact : part.path("taskFactIds")) {
                            if (fact.asText().equals(correction.path("sourceFactId").asText())) matchingFact = true;
                        }
                        if (!"CONFLICT".equals(part.path("status").asText()) || !matchingFact) continue;
                    }
                    for (JsonNode evidence : part.path("evidence")) {
                        if (evidence.path("segmentId").asText().equals(correction.path("segmentId").asText())
                                && evidence.path("quote").asText().contains(correction.path("patch").path("original").asText())) grounded = true;
                    }
                }
                if (!grounded) fail();
                validateFullSegment(correction, observation);
            }
            if (typed) {
                // 교정의 권한을 명시 사실과 실제 언어 형태 오류로 나누며, 구형 결과는 그대로 보존한다.
                String issue = correction.path("issueType").asText();
                String reason = correction.path("formReason").asText();
                JsonNode fact = correction.path("sourceFactId");
                if (!fact.isTextual()) fail();
                if ("LANGUAGE_FORM".equals(issue)) {
                    if (!fact.asText().isEmpty() || !Set.of("SPELLING", "INFLECTION", "SYNTAX").contains(reason)) fail();
                    String original = java.text.Normalizer.normalize(correction.path("original").asText(), java.text.Normalizer.Form.NFKC).replaceAll("[^\\p{L}\\p{N}]", "");
                    String replacement = java.text.Normalizer.normalize(correction.path("replacement").asText(), java.text.Normalizer.Form.NFKC).replaceAll("[^\\p{L}\\p{N}]", "");
                    if (original.equals(replacement)) fail();
                } else if (!"TASK_FACT_CONFLICT".equals(issue) || !fact.asText().matches("T[1-9][0-9]*") || !"NONE".equals(reason)
                        || !"UNMET".equals(observation.path("status").asText()) || !hasRelation(observation, "CONFLICT")) fail();
            }
        }
        for (JsonNode alternative : result.path("optionalAlternatives")) {
            if (!Set.of("EXPRESSION_ONLY", "HYPOTHETICAL_EXAMPLE").contains(alternative.path("suggestionKind").asText())
                    || alternative.path("original").asText().isEmpty()
                    || !answer.contains(alternative.path("original").asText())) fail();
            if (Set.of("curated-writing-feedback-v5", "curated-writing-feedback-v6", "curated-writing-feedback-v7").contains(result.path("policyVersion").asText())) {
                // 표현만 다듬는 제안이 사실·순서·입장을 바꾸지 않도록 새 계약의 관계 근거를 확인한다.
                for (String key : Set.of("causality", "chronology", "specificity", "stance")) {
                    String check = alternative.path("relationChecks").path(key).asText();
                    if (!Set.of("PRESERVED", "CONFLICT", "NOT_APPLICABLE", "UNCERTAIN").contains(check)) fail();
                    if ("EXPRESSION_ONLY".equals(alternative.path("suggestionKind").asText())
                            && Set.of("CONFLICT", "UNCERTAIN").contains(check)) fail();
                }
            }
            if (structured) {
                validatePatch(alternative);
                JsonNode observation = observations.get(alternative.path("requirementId").asText());
                if (observation == null || !alternative.path("segmentId").asText().matches("S[1-9][0-9]*")) fail();
                if ("EXPRESSION_ONLY".equals(alternative.path("suggestionKind").asText())) {
                    boolean linked = false;
                    for (JsonNode segment : observation.path("segmentIds")) {
                        if (segment.asText().equals(alternative.path("segmentId").asText())) linked = true;
                    }
                    if (!"MET".equals(observation.path("status").asText()) || !linked) fail();
                    validateFullSegment(alternative, observation);
                    for (JsonNode correction : result.path("necessaryCorrections")) {
                        if (correction.path("segmentId").asText().equals(alternative.path("segmentId").asText())) fail();
                    }
                }
            }
        }
    }

    private static void validateRequirementParts(JsonNode observation, String answer, JsonNode requirements) {
        // 요소별 존재 근거로 집계한 상태를 확인한다. 부분 누락의 정상 근거는 parts 안에 남긴다.
        JsonNode parts = observation.path("requirementParts");
        if (!parts.isArray() || parts.isEmpty() || !observation.path("requirementId").asText().matches("C[1-9][0-9]*")) fail();
        StringBuilder coverage = new StringBuilder();
        Set<String> statuses = new HashSet<>();
        java.util.LinkedHashSet<String> segments = new java.util.LinkedHashSet<>();
        for (JsonNode part : parts) {
            JsonNode quote = part.path("requirementQuote");
            String status = part.path("status").asText();
            if (!quote.isTextual() || quote.asText().isEmpty() || !Set.of("PRESENT", "ABSENT", "CONFLICT", "UNCERTAIN").contains(status)
                    || !part.path("evidence").isArray() || !part.path("taskFactIds").isArray()) fail();
            coverage.append(quote.asText());
            statuses.add(status);
            if ("ABSENT".equals(status) != part.path("evidence").isEmpty()) fail();
            for (JsonNode evidence : part.path("evidence")) {
                if (!evidence.path("segmentId").asText().matches("S[1-9][0-9]*") || !evidence.path("quote").isTextual()
                        || evidence.path("quote").asText().isBlank() || !answer.contains(evidence.path("quote").asText())) fail();
                segments.add(evidence.path("segmentId").asText());
            }
            for (JsonNode fact : part.path("taskFactIds")) {
                if (!fact.isTextual() || !fact.asText().matches("T[1-9][0-9]*")) fail();
            }
        }
        if (requirements.isArray() && !requirements.isEmpty()) {
            boolean matched = false;
            for (int index = 0; index < requirements.size(); index++) {
                if (("C" + (index + 1)).equals(observation.path("requirementId").asText())) {
                    matched = coverage.toString().equals(requirements.get(index).asText());
                }
            }
            if (!matched) fail();
        }

        // 관계의 자기신고와 요소 집계가 모순되면 통과시키지 않는다. 의미 자체의 정확성은 별도 검증한다.
        boolean absent = statuses.contains("ABSENT");
        boolean conflict = statuses.contains("CONFLICT");
        boolean uncertain = statuses.contains("UNCERTAIN");
        String expectedStatus = absent || conflict ? "UNMET" : uncertain ? "UNCERTAIN" : "MET";
        boolean missing = segments.isEmpty();
        if (!expectedStatus.equals(observation.path("status").asText())
                || !(missing ? "MISSING_REQUIREMENT" : "LEARNER_TEXT").equals(observation.path("evidenceKind").asText())) fail();
        for (String key : Set.of("causality", "chronology", "specificity", "stance")) {
            if (!Set.of("PRESERVED", "CONFLICT", "NOT_APPLICABLE", "UNCERTAIN").contains(observation.path("relationChecks").path(key).asText())) fail();
        }
        if (hasRelation(observation, "CONFLICT") != conflict || hasRelation(observation, "UNCERTAIN") != uncertain) fail();
        if (!missing) {
            java.util.List<String> actual = new java.util.ArrayList<>();
            for (JsonNode segment : observation.path("segmentIds")) actual.add(segment.asText());
            if (!actual.equals(new java.util.ArrayList<>(segments))) fail();
            for (JsonNode part : parts) {
                for (JsonNode evidence : part.path("evidence")) {
                    int index = actual.indexOf(evidence.path("segmentId").asText());
                    if (index < 0 || !observation.path("answerQuotes").get(index).asText().contains(evidence.path("quote").asText())) fail();
                }
            }
        }
    }

    private static void validateFullSegment(JsonNode action, JsonNode observation) {
        for (int index = 0; index < observation.path("segmentIds").size(); index++) {
            if (observation.path("segmentIds").get(index).asText().equals(action.path("segmentId").asText())) {
                if (!observation.path("answerQuotes").get(index).asText().equals(action.path("original").asText())) fail();
                return;
            }
        }
        fail();
    }

    private static void validatePatch(JsonNode action) {
        // 한 번 등장하는 원문 부분만 교체하고 나머지 문장 내용을 그대로 보존한다.
        JsonNode patch = action.path("patch");
        if (!action.path("original").isTextual() || !action.path("replacement").isTextual()
                || !patch.path("original").isTextual() || !patch.path("replacement").isTextual()) fail();
        String original = action.path("original").asText();
        String replacement = action.path("replacement").asText();
        String from = patch.path("original").asText();
        String to = patch.path("replacement").asText();
        int index = original.indexOf(from);
        if (from.isEmpty() || from.equals(to) || index < 0 || index != original.lastIndexOf(from)
                || replacement.isBlank() || replacement.length() > 4000
                || !replacement.equals(original.substring(0, index) + to + original.substring(index + from.length()))) fail();
    }

    private static void validateRelations(JsonNode observation) {
        // v4의 관계 근거와 상태가 모순되면 사용자에게 충족으로 표시하지 않는다.
        JsonNode checks = observation.path("relationChecks");
        for (String key : Set.of("causality", "chronology", "specificity", "stance")) {
            if (!Set.of("PRESERVED", "CONFLICT", "NOT_APPLICABLE", "UNCERTAIN").contains(checks.path(key).asText())) fail();
        }
        String status = observation.path("status").asText();
        boolean conflict = hasRelation(observation, "CONFLICT");
        boolean uncertain = hasRelation(observation, "UNCERTAIN");
        if (!Set.of("MET", "UNMET", "UNCERTAIN").contains(status)
                || ("MET".equals(status) && (conflict || uncertain))
                || (conflict && !"UNMET".equals(status))
                || (!conflict && uncertain && !"UNCERTAIN".equals(status))) fail();
        if ("MISSING_REQUIREMENT".equals(observation.path("evidenceKind").asText())) {
            for (String key : Set.of("causality", "chronology", "specificity", "stance")) {
                if (!"NOT_APPLICABLE".equals(checks.path(key).asText())) fail();
            }
        }
    }

    private static boolean hasRelation(JsonNode observation, String value) {
        for (String key : Set.of("causality", "chronology", "specificity", "stance")) {
            if (value.equals(observation.path("relationChecks").path(key).asText())) return true;
        }
        return false;
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
