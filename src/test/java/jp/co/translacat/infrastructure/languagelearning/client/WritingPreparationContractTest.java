package jp.co.translacat.infrastructure.languagelearning.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class WritingPreparationContractTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    static ObjectNode success() {
        var value = JSON.createObjectNode()
                .put("requestKey", "prep-1").put("preparationId", "saved-id").put("status", "SUCCEEDED")
                .put("policyVersion", "curated-writing-generation-v2").put("catalogId", "gen-saved-id")
                .put("reviewStatus", "DRAFT").put("qaOnly", true).putNull("failureCode")
                .put("provider", "openai").put("model", "gpt-5.6-luna").put("requiresReconciliation", false)
                .put("writingType", "TRANSLATION").put("targetBand", 2).put("topicKey", "clinic-appointment")
                .putNull("repairOfRequestKey").putNull("repairNotes").put("repairMode", "CORE_REPLACEMENT")
                .put("canRepair", true).putNull("repairBlockedReason");
        value.putObject("quality").put("contractStatus", "PASSED").put("semanticStatus", "NOT_REVIEWED")
                .put("humanReview", "NOT_PERFORMED").put("releaseStatus", "QA_ONLY")
                .putArray("reasonCodes").add("AUTOMATED_CHECK_ONLY").add("CORE_CONTENT_FIXED");
        var candidate = value.putObject("candidate").put("catalogId", "gen-saved-id")
                .put("contentHash", "a".repeat(64)).put("writingType", "TRANSLATION").put("targetBand", 2)
                .put("topicKey", "clinic-appointment").put("prompt", "예약 변경을 일본어로 전달하세요.");
        candidate.putArray("providedFacts").add("예약은 수요일입니다.");
        candidate.putArray("requiredIntents");
        candidate.putArray("responseConstraints").add("일본어로 작성하세요.");
        candidate.putArray("visibleRequirements").add("변경된 시각을 전달하세요.");
        return value;
    }

    private ObjectNode state(String status) {
        var value = success();
        value.put("status", status).putNull("candidate");
        value.put("canRepair", false).put("repairBlockedReason", "NOT_FINISHED");
        ((ObjectNode) value.path("quality")).put("contractStatus", "PENDING");
        if (!"GENERATED".equals(status)) {
            value.putNull("provider").putNull("model").put("requiresReconciliation", true);
            ((ObjectNode) value.path("quality")).withArray("reasonCodes").add("EXECUTION_UNCERTAIN");
        }
        if ("UNCERTAIN".equals(status)) value.put("failureCode", "MODEL_EXECUTION_TRANSPORT");
        return value;
    }

    @Test
    void acceptsThreeTypesWithoutTreatingStructureAsSemanticApproval() {
        // 준비: 각 유형의 공개 문제와 동일한 의미 검수 미실행 상태.
        for (String type : List.of("TRANSLATION", "GUIDED", "FREE")) {
            var value = success().put("writingType", type);
            ((ObjectNode) value.path("candidate")).put("writingType", type);
            if (!"TRANSLATION".equals(type)) {
                value.put("repairMode", "CONTENT_REPAIR");
                ((ObjectNode) value.path("quality")).putArray("reasonCodes").add("AUTOMATED_CHECK_ONLY");
            }

            // 실행·검증: 원문 객체를 그대로 전달하고 승격 필드를 만들지 않는다.
            assertSame(value, WritingPreparationContract.response(value));
            assertEquals("NOT_REVIEWED", value.path("quality").path("semanticStatus").textValue());
        }
    }

    @Test
    void acceptsPendingAndAmbiguousStatesWithoutRepairOrPublicCandidate() {
        // 준비·실행·검증: GENERATED 조회도 자동 저장 복구나 재호출을 의미하지 않는다.
        for (String status : List.of("RUNNING", "GENERATED", "UNCERTAIN")) {
            var value = state(status);
            assertSame(value, WritingPreparationContract.response(value));
        }
    }

    @Test
    void distinguishesSavedContentFailureFromProviderFailure() {
        // 준비: 콘텐츠는 저장되었지만 검증에서 거절된 경우.
        var value = success().put("status", "FAILED").putNull("candidate")
                .put("failureCode", "WRITING_GENERATION_CONTENT_INVALID");
        var quality = (ObjectNode) value.path("quality");
        quality.put("contractStatus", "FAILED").withArray("reasonCodes").add("CONTENT_CHECK_FAILED");

        // 실행·검증: 저장 콘텐츠만 명시적으로 수정할 수 있다.
        assertSame(value, WritingPreparationContract.response(value));
        value.put("failureCode", "MODEL_UNAVAILABLE").putNull("provider").putNull("model")
                .put("canRepair", false).put("repairBlockedReason", "NO_SAVED_CONTENT");
        quality.putArray("reasonCodes").add("AUTOMATED_CHECK_ONLY").add("CORE_CONTENT_FIXED").add("PROVIDER_FAILED");
        assertSame(value, WritingPreparationContract.response(value));
    }

    @Test
    void preservesOriginalTopicAndOwnerNoteWhenCoreReplacementUsesAnotherTopic() {
        // 준비: 원본은 같은 요청 주제이고 실제 대체 문항은 다른 의미 주제다.
        var value = success().put("repairOfRequestKey", "prep-original").put("repairNotes", "주제가 너무 반복돼요.")
                .put("canRepair", false).put("repairBlockedReason", "REPAIR_LIMIT");
        ((ObjectNode) value.path("candidate")).put("topicKey", "reading-group-change");

        // 실행·검증: 원본과 새 후보를 구별하고 수정 후보의 추가 재생성을 허용하지 않는다.
        assertSame(value, WritingPreparationContract.response(value));
        value.put("canRepair", true).putNull("repairBlockedReason");
        rejected(value);
    }

    @Test
    void acceptsExhaustionAndPriorPolicyWithoutPromotingDrafts() {
        // 준비: 과거 저장 정책은 유지하며 유한 코어 소진을 표시한다.
        var value = success().put("policyVersion", "curated-writing-generation-v1")
                .put("canRepair", false).put("repairBlockedReason", "CONTENT_EXHAUSTED");

        // 실행·검증
        assertSame(value, WritingPreparationContract.response(value));
        value.put("reviewStatus", "APPROVED");
        rejected(value);
    }

    @Test
    void rejectsEveryMissingRequiredField() {
        // 준비: nullable도 필드 자체가 사라지면 계약 누락이다.
        var original = success();
        List<String> keys = new java.util.ArrayList<>();
        original.fieldNames().forEachRemaining(keys::add);

        // 실행·검증
        for (String key : keys) {
            var value = original.deepCopy();
            value.remove(key);
            rejected(value);
        }
    }

    @Test
    void rejectsHiddenAnswersExamplesAndOfficialMetricsAtEveryObjectBoundary() {
        // 준비: 준비 결과에는 비공개 정답·평가 필드가 없어야 한다.
        for (String field : List.of("referenceAnswers", "clearError", "boundaryAnswer", "levelEvidence", "score", "metric", "profile")) {
            for (String location : List.of("root", "quality", "candidate")) {
                var value = success();
                var target = "root".equals(location) ? value : (ObjectNode) value.path(location);
                target.put(field, "hidden");

                // 실행·검증
                rejected(value);
            }
        }
    }

    @Test
    void rejectsCoercedTypesAndInvalidNullableValues() {
        // 준비
        List<Consumer<ObjectNode>> mutations = List.of(
                value -> value.put("targetBand", "2"), value -> value.put("targetBand", 2.5),
                value -> value.put("targetBand", 0), value -> value.put("targetBand", 6),
                value -> value.put("qaOnly", "true"), value -> value.put("canRepair", 1),
                value -> value.put("requiresReconciliation", "false"), value -> value.put("repairNotes", ""),
                value -> value.put("provider", " "), value -> value.put("model", 1),
                value -> value.put("failureCode", false), value -> value.put("topicKey", "../other"),
                value -> value.put("requestKey", "../other"));

        // 실행·검증
        for (var mutation : mutations) {
            var value = success();
            mutation.accept(value);
            rejected(value);
        }
    }

    @Test
    void rejectsCandidateMismatchAndMalformedVisibleContent() {
        // 준비
        List<Consumer<ObjectNode>> mutations = List.of(
                item -> item.put("catalogId", "other"), item -> item.put("writingType", "FREE"),
                item -> item.put("targetBand", 3), item -> item.put("targetBand", "2"),
                item -> item.put("contentHash", "bad-hash"), item -> item.put("topicKey", "other-topic"),
                item -> item.put("prompt", ""), item -> item.put("providedFacts", "text"),
                item -> item.putArray("visibleRequirements").add(1), item -> item.remove("requiredIntents"));

        // 실행·검증
        for (var mutation : mutations) {
            var value = success();
            mutation.accept((ObjectNode) value.path("candidate"));
            rejected(value);
        }
    }

    @Test
    void rejectsSemanticPromotionAndForgedOrDuplicateReasonCodes() {
        // 준비
        List<Consumer<ObjectNode>> mutations = List.of(
                quality -> quality.put("contractStatus", "PENDING"),
                quality -> quality.put("semanticStatus", "PASSED"), quality -> quality.put("humanReview", "APPROVED"),
                quality -> quality.put("releaseStatus", "PUBLIC"),
                quality -> quality.withArray("reasonCodes").add("MEANING_CORRECT"),
                quality -> quality.withArray("reasonCodes").add("AUTOMATED_CHECK_ONLY"),
                quality -> quality.putArray("reasonCodes").add("CORE_CONTENT_FIXED"),
                quality -> quality.withArray("reasonCodes").add("CONTENT_CHECK_FAILED"));

        // 실행·검증
        for (var mutation : mutations) {
            var value = success();
            mutation.accept((ObjectNode) value.path("quality"));
            rejected(value);
        }
    }

    @Test
    void rejectsContradictoryStatusRepairAndReconciliation() {
        // 준비
        List<Consumer<ObjectNode>> mutations = List.of(
                value -> value.put("requiresReconciliation", true), value -> value.putNull("candidate"),
                value -> value.put("canRepair", false), value -> value.put("repairMode", "CONTENT_REPAIR"),
                value -> value.put("repairOfRequestKey", "original"), value -> value.putNull("provider"),
                value -> value.put("failureCode", "MODEL_ERROR"),
                value -> value.put("canRepair", false).put("repairBlockedReason", "NO_SAVED_CONTENT"),
                value -> value.put("canRepair", false).put("repairBlockedReason", "NOT_FINISHED"));

        // 실행·검증
        for (var mutation : mutations) {
            var value = success();
            mutation.accept(value);
            rejected(value);
        }
        var uncertain = state("UNCERTAIN").put("canRepair", true).putNull("repairBlockedReason");
        rejected(uncertain);
    }

    @Test
    void rejectsSuccessResponseForAnotherRequestOrChangedOwnerNote() {
        // 준비
        var value = success();
        var request = JSON.createObjectNode().put("requestKey", "prep-1").put("writingType", "TRANSLATION")
                .put("targetBand", 2).put("topicKey", "clinic-appointment");

        // 실행·검증: 정상 요청의 응답만 통과한다.
        assertSame(value, WritingPreparationContract.matchesRequest(value, request));
        request.put("requestKey", "prep-other");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPreparationContract.matchesRequest(value, request));
        request.put("requestKey", "prep-1").put("repairOfRequestKey", "original").put("repairNotes", "saved note");
        value.put("repairOfRequestKey", "original").put("repairNotes", "changed note");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPreparationContract.matchesRequest(value, request));
    }

    private void rejected(ObjectNode value) {
        var error = assertThrows(LanguageLearningServiceException.class, () -> WritingPreparationContract.response(value));
        assertEquals("LL_WRITING_CONTRACT_ERROR", error.getErrorCode());
    }
}
