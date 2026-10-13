package jp.co.translacat.infrastructure.languagelearning.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;

import java.util.HashSet;
import java.util.Set;

/** 구조 검사 통과와 의미 품질·사람 검수를 구별하고, 제출 전 비공개 답안 유출을 막는다. */
final class WritingPreparationContract {
    private static final Set<String> FIELDS = Set.of(
            "requestKey", "preparationId", "status", "policyVersion", "catalogId", "reviewStatus", "qaOnly",
            "failureCode", "provider", "model", "requiresReconciliation", "writingType", "targetBand", "topicKey",
            "repairOfRequestKey", "repairNotes", "repairMode", "canRepair", "repairBlockedReason", "quality", "candidate");
    private static final Set<String> CANDIDATE_FIELDS = Set.of(
            "catalogId", "contentHash", "writingType", "targetBand", "topicKey", "prompt", "providedFacts",
            "requiredIntents", "responseConstraints", "visibleRequirements");
    private static final Set<String> TYPES = Set.of("TRANSLATION", "GUIDED", "FREE");

    private WritingPreparationContract() { }

    static JsonNode response(JsonNode value) {
        // 추가 필드도 거절하여 점수·참고 답안이 준비 응답에 섞이지 않도록 한다.
        fields(value, FIELDS);
        String requestKey = text(value, "requestKey");
        String status = text(value, "status");
        String type = text(value, "writingType");
        int band = integer(value, "targetBand");
        if (!requestKey.matches("[a-zA-Z0-9._-]{1,80}")
                || !Set.of("RUNNING", "GENERATED", "SUCCEEDED", "FAILED", "UNCERTAIN").contains(status)
                || !Set.of("curated-writing-generation-v1", "curated-writing-generation-v2")
                .contains(text(value, "policyVersion"))
                || !TYPES.contains(type) || band < 1 || band > 5
                || !text(value, "topicKey").matches("[a-z0-9][a-z0-9-]{2,79}")
                || !"DRAFT".equals(text(value, "reviewStatus")) || !bool(value, "qaOnly")) fail();
        text(value, "preparationId");
        text(value, "catalogId");

        String failureCode = nullableText(value, "failureCode");
        String provider = nullableText(value, "provider");
        String model = nullableText(value, "model");
        boolean unfinished = Set.of("RUNNING", "GENERATED", "UNCERTAIN").contains(status);
        boolean uncertain = Set.of("RUNNING", "UNCERTAIN").contains(status);
        if (bool(value, "requiresReconciliation") != uncertain
                || (provider == null) != (model == null)
                || (Set.of("FAILED", "UNCERTAIN").contains(status) != (failureCode != null))
                || (Set.of("SUCCEEDED", "GENERATED").contains(status) && provider == null)) fail();

        // 수정 가능 여부와 모드는 저장 상태에 맞아야 하며, 수정 후보를 다시 잇는 것은 금지한다.
        String repairOf = nullableText(value, "repairOfRequestKey");
        String notes = nullableText(value, "repairNotes");
        String blocked = nullableText(value, "repairBlockedReason");
        boolean core = "TRANSLATION".equals(type) && band == 2;
        if ((repairOf == null) != (notes == null)
                || (repairOf != null && (repairOf.equals(requestKey)
                || !repairOf.matches("[a-zA-Z0-9._-]{1,80}") || notes.length() > 2000))
                || !(core ? "CORE_REPLACEMENT" : "CONTENT_REPAIR").equals(text(value, "repairMode"))
                || bool(value, "canRepair") != (blocked == null)
                || (blocked != null && !Set.of("NOT_FINISHED", "NO_SAVED_CONTENT", "REPAIR_LIMIT", "CONTENT_EXHAUSTED").contains(blocked))
                || (repairOf != null && !"REPAIR_LIMIT".equals(blocked))
                || (unfinished && !Set.of("NOT_FINISHED", "REPAIR_LIMIT").contains(blocked == null ? "" : blocked))
                || (!unfinished && "NOT_FINISHED".equals(blocked))
                || ("NO_SAVED_CONTENT".equals(blocked) && !"FAILED".equals(status))
                || ("CONTENT_EXHAUSTED".equals(blocked) && !core)) fail();

        // 사유는 자동 검사·실행 실패의 출처만 나타낸다. 의미 품질 승격은 허용하지 않는다.
        JsonNode quality = value.path("quality");
        fields(quality, Set.of("contractStatus", "semanticStatus", "humanReview", "releaseStatus", "reasonCodes"));
        String expectedContract = "SUCCEEDED".equals(status) ? "PASSED" : "FAILED".equals(status) ? "FAILED" : "PENDING";
        if (!expectedContract.equals(text(quality, "contractStatus"))
                || !"NOT_REVIEWED".equals(text(quality, "semanticStatus"))
                || !"NOT_PERFORMED".equals(text(quality, "humanReview"))
                || !"QA_ONLY".equals(text(quality, "releaseStatus"))) fail();
        Set<String> expectedReasons = new HashSet<>(Set.of("AUTOMATED_CHECK_ONLY"));
        if (core) expectedReasons.add("CORE_CONTENT_FIXED");
        if (uncertain) expectedReasons.add("EXECUTION_UNCERTAIN");
        if ("FAILED".equals(status)) expectedReasons.add(failureCode.startsWith("WRITING_GENERATION_")
                || failureCode.startsWith("WRITING_B2_CORE_") ? "CONTENT_CHECK_FAILED" : "PROVIDER_FAILED");
        if (!expectedReasons.equals(textArray(quality, "reasonCodes", true))) fail();

        // 성공 시에도 공개 문제 본문만 전달하며 응답의 요청 문맥과 후보 문맥을 대조한다.
        JsonNode candidate = value.path("candidate");
        if ("SUCCEEDED".equals(status)) {
            fields(candidate, CANDIDATE_FIELDS);
            if (!text(value, "catalogId").equals(text(candidate, "catalogId"))
                    || !type.equals(text(candidate, "writingType")) || band != integer(candidate, "targetBand")
                    || !text(candidate, "contentHash").matches("[0-9a-fA-F]{64}")) fail();
            String selectedTopic = text(candidate, "topicKey");
            if (!selectedTopic.matches("[a-z0-9][a-z0-9-]{2,79}")
                    || (!(core && repairOf != null) && !selectedTopic.equals(text(value, "topicKey")))) fail();
            text(candidate, "prompt");
            for (String field : new String[] {"providedFacts", "requiredIntents", "responseConstraints", "visibleRequirements"}) {
                textArray(candidate, field, false);
            }
        } else if (!candidate.isNull()) fail();
        return value;
    }

    static JsonNode matchesRequest(JsonNode value, JsonNode request) {
        // 입력의 원래 JSON 타입을 보존한 채 성공 응답이 다른 요청으로 바뀌지 않았는지 확인한다.
        if (request == null || !request.isObject()) fail();
        for (String key : new String[] {"requestKey", "writingType", "targetBand", "topicKey"}) {
            if (!value.path(key).equals(request.path(key))) fail();
        }
        for (String key : new String[] {"repairOfRequestKey", "repairNotes"}) {
            if (request.has(key) ? !value.path(key).equals(request.path(key)) : !value.path(key).isNull()) fail();
        }
        return value;
    }

    private static void fields(JsonNode value, Set<String> expected) {
        if (value == null || !value.isObject()) fail();
        Set<String> actual = new HashSet<>();
        value.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) fail();
    }

    private static String text(JsonNode value, String key) {
        JsonNode field = value.path(key);
        if (!field.isTextual() || field.textValue().isBlank()) fail();
        return field.textValue();
    }

    private static String nullableText(JsonNode value, String key) {
        return value.path(key).isNull() ? null : text(value, key);
    }

    private static int integer(JsonNode value, String key) {
        JsonNode field = value.path(key);
        if (!field.isIntegralNumber() || !field.canConvertToInt()) fail();
        return field.intValue();
    }

    private static boolean bool(JsonNode value, String key) {
        if (!value.path(key).isBoolean()) fail();
        return value.path(key).booleanValue();
    }

    private static Set<String> textArray(JsonNode value, String key, boolean unique) {
        JsonNode values = value.path(key);
        if (!values.isArray()) fail();
        Set<String> result = new HashSet<>();
        for (JsonNode entry : values) {
            if (!entry.isTextual() || entry.textValue().isBlank()) fail();
            if (!result.add(entry.textValue()) && unique) fail();
        }
        return result;
    }

    private static void fail() {
        throw new LanguageLearningServiceException(HttpStatus.BAD_GATEWAY, "LL_WRITING_CONTRACT_ERROR",
                "Writing 서비스 응답 계약이 올바르지 않습니다.");
    }
}
