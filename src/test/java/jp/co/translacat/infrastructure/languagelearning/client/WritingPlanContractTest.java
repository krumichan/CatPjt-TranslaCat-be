package jp.co.translacat.infrastructure.languagelearning.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class WritingPlanContractTest {
    private final ObjectMapper json = new ObjectMapper();

    private ObjectNode plan(int target, int acquired) {
        ObjectNode value = json.createObjectNode();
        value.put("policyVersion", WritingPlanContract.POLICY).put("resultPolicy", "REFERENCE_ONLY")
                .put("targetItemCount", target).put("acquiredItemCount", acquired)
                .put("remainingItemCount", target - acquired).put("submittedItemCount", 0)
                .put("feedbackCompletedItemCount", 0).put("sentenceCount", target)
                .put("generatedItemCount", acquired).put("planRevision", 1)
                .put("personalizedFeedbackStatus", "DISABLED_PENDING_QUALITY_GATE")
                .put("status", target == acquired ? "READY" : "PARTIAL");
        var items = value.putArray("items");
        for (int order = 1; order <= acquired; order++) {
            var item = items.addObject();
            item.put("itemId", -order).put("order", order).put("answered", false).putArray("attempts");
        }
        return value;
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 5, 10, 20, 30, 50, 100, 101})
    void preservesVariableCountWithoutFiveOrHundredSchemaLimit(int target) {
        // 준비 / 실행 / 검증: 서버의 설정 상한과 중계 schema의 수량 단위를 분리한다.
        var value = plan(target, target);
        assertSame(value, WritingPlanContract.plan(value));
    }

    @Test
    void partialTwentySevenDoesNotClaimThirtyCompleted() {
        // 준비: 정상27개를 그대로 둔다.
        var value = plan(30, 27);
        assertSame(value, WritingPlanContract.plan(value));

        // 실행 / 검증: 부분 확보 상태를 전체 완료로 바꾼 응답은 거절한다.
        value.put("status", "COMPLETED");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

    @Test
    void rejectsPreSubmissionReferenceAndUnknownPolicy() {
        var value = plan(1, 1);
        ((ObjectNode) value.path("items").get(0)).putObject("reference").put("answer", "hidden");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        ((ObjectNode) value.path("items").get(0)).remove("reference");
        value.put("policyVersion", "curated-writing-v1");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

    @Test
    void rejectsCoercedFractionOverflowDuplicateAndWrongCount() {
        var value = plan(2, 2);
        value.put("targetItemCount", 2.5);
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        value.put("targetItemCount", Long.MAX_VALUE);
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        value.put("targetItemCount", 2);
        ((ObjectNode) value.path("items").get(1)).put("itemId", -1);
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

    @Test
    void referenceAnswerDoesNotCountAsPersonalizedFeedback() {
        var value = plan(1, 1);
        value.put("submittedItemCount", 1).put("status", "COMPLETED");
        var item = (ObjectNode) value.path("items").get(0);
        item.put("answered", true);
        item.putArray("attempts").addObject().put("resultPolicy", "REFERENCE_ONLY").putNull("evaluation");
        item.putObject("reference").putArray("referenceAnswers").add("参考回答");
        assertSame(value, WritingPlanContract.plan(value));
        assertEquals(0, value.path("feedbackCompletedItemCount").asInt());
        ((ObjectNode) item.path("attempts").get(0)).put("evaluation", 0);
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

    @Test
    void activeFeedbackMustBeBoundToActualAnswerAndRemainNonScoring() {
        // 준비: 실제 LL의 활성화 상태·원문·체크리스트 구조를 가진 답안.
        var value = plan(1, 1);
        value.put("personalizedFeedbackStatus", "ACTIVE_QA").put("submittedItemCount", 1)
                .put("feedbackCompletedItemCount", 1).put("status", "COMPLETED");
        var item = (ObjectNode) value.path("items").get(0);
        item.put("answered", true);
        item.putObject("reference").putArray("checklist").add("전달 내용");
        var attempt = item.putArray("attempts").addObject();
        attempt.put("answer", "欠席した生徒も増えていました。").put("resultPolicy", "REFERENCE_ONLY").putNull("evaluation");
        var feedback = attempt.putObject("feedback");
        feedback.put("status", "SUCCEEDED").put("canRetry", false).putNull("failureCode");
        var result = feedback.putObject("result");
        result.put("policyVersion", "curated-writing-feedback-v1").put("resultPolicy", "REFERENCE_ONLY");
        result.putArray("observations").addObject().put("requirementId", "C1");
        result.putArray("necessaryCorrections");
        result.putArray("optionalAlternatives");
        result.putArray("uncertainties");

        // 실행·검증: 성공1을 통과시키되 잘못된 교정 원문과 점수형 evaluation은 거절한다.
        assertSame(value, WritingPlanContract.plan(value));
        result.withArray("necessaryCorrections").addObject().put("original", "増っていました");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        result.withArray("necessaryCorrections").removeAll();
        attempt.put("evaluation", 0);
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }
}
