package jp.co.translacat.infrastructure.languagelearning.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Set;

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

        // 새 계약: 누락은 요구 근거로만 설명하며 개인 사실을 채운 교정은 거절한다.
        result.put("policyVersion", "curated-writing-feedback-v2");
        var observation = (ObjectNode) result.path("observations").get(0);
        observation.put("status", "UNMET").put("evidenceKind", "MISSING_REQUIREMENT");
        observation.putArray("segmentIds");
        observation.putArray("answerQuotes");
        assertSame(value, WritingPlanContract.plan(value));
        result.put("policyVersion", "curated-writing-feedback-v3");
        assertSame(value, WritingPlanContract.plan(value));
        result.withArray("necessaryCorrections").addObject().put("requirementId", "C1")
                .put("segmentId", "S1").put("original", "欠席した生徒も増えていました。")
                .put("replacement", "出張があるため変更してください。");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        result.withArray("necessaryCorrections").removeAll();
        var alternative = result.withArray("optionalAlternatives").addObject()
                .put("original", "欠席した生徒も増えていました。");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        alternative.put("suggestionKind", "HYPOTHETICAL_EXAMPLE");
        assertSame(value, WritingPlanContract.plan(value));
        attempt.put("evaluation", 0);
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

    @Test
    void visibleRequirementsAreTextNotPrivateEvaluationKeys() {
        // 준비·실행
        var value = plan(1, 1);
        var item = (ObjectNode) value.path("items").get(0);
        item.putArray("taskRequirements").add("이유를 설명하세요.");
        assertSame(value, WritingPlanContract.plan(value));

        // 검증
        item.withArray("taskRequirements").addObject().put("answer", "private");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

    private ObjectNode typedPlan() {
        var value = plan(1, 1);
        value.put("personalizedFeedbackStatus", "ACTIVE_QA").put("submittedItemCount", 1).put("feedbackCompletedItemCount", 1).put("status", "COMPLETED");
        var item = (ObjectNode) value.path("items").get(0);
        item.put("answered", true).putObject("reference").putArray("checklist").add("시각 전달");
        var attempt = item.withArray("attempts").addObject().put("answer", "午前九時に始まります。").put("resultPolicy", "REFERENCE_ONLY").putNull("evaluation");
        var result = attempt.putObject("feedback").put("status", "SUCCEEDED").put("canRetry", false).putObject("result");
        result.put("policyVersion", "curated-writing-feedback-v4").put("resultPolicy", "REFERENCE_ONLY");
        var observation = result.putArray("observations").addObject().put("requirementId", "C1").put("status", "MET").put("evidenceKind", "LEARNER_TEXT");
        observation.putArray("segmentIds").add("S1");
        observation.putArray("answerQuotes").add("午前九時に始まります。");
        observation.putObject("relationChecks").put("causality", "NOT_APPLICABLE").put("chronology", "PRESERVED").put("specificity", "PRESERVED").put("stance", "NOT_APPLICABLE");
        result.putArray("necessaryCorrections");
        result.putArray("optionalAlternatives");
        result.putArray("uncertainties");
        return value;
    }

    private ObjectNode result(ObjectNode value) {
        return (ObjectNode) value.path("items").get(0).path("attempts").get(0).path("feedback").path("result");
    }

    @Test
    void typedRelationsRejectContradictoryFulfilment() {
        // 준비·실행
        var value = typedPlan();
        var observation = (ObjectNode) result(value).path("observations").get(0);
        var relations = (ObjectNode) observation.path("relationChecks");
        assertSame(value, WritingPlanContract.plan(value));

        // 검증: 빠진 관계·충돌·불확실을 충족으로 중계하지 않는다.
        for (String invalid : new String[] {"", "UNKNOWN", "CONFLICT", "UNCERTAIN"}) {
            relations.put("chronology", invalid);
            assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        }
        observation.put("status", "UNCERTAIN");
        assertSame(value, WritingPlanContract.plan(value));
        observation.put("status", "UNMET");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

    @Test
    void typedFactCorrectionRequiresPublicFactAndConflict() {
        // 준비: 실제 인용과 공개 사실 식별자를 가진 사실 교정.
        var value = typedPlan();
        var result = result(value);
        var observation = (ObjectNode) result.path("observations").get(0);
        observation.put("status", "UNMET");
        ((ObjectNode) observation.path("relationChecks")).put("chronology", "CONFLICT");
        var correction = result.withArray("necessaryCorrections").addObject().put("requirementId", "C1").put("segmentId", "S1")
                .put("original", "午前九時").put("replacement", "午前十時").put("issueType", "TASK_FACT_CONFLICT").put("sourceFactId", "T1").put("formReason", "NONE");

        // 실행·검증
        assertSame(value, WritingPlanContract.plan(value));
        for (String invalid : new String[] {"", "C1", "T0"}) {
            correction.put("sourceFactId", invalid);
            assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        }
        correction.put("sourceFactId", "T1");
        ((ObjectNode) observation.path("relationChecks")).put("chronology", "PRESERVED");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

    @Test
    void typedLanguageFormDoesNotMakePunctuationMandatory() {
        // 준비
        var value = typedPlan();
        var correction = result(value).withArray("necessaryCorrections").addObject().put("requirementId", "C1").put("segmentId", "S1")
                .put("original", "始まります").put("replacement", "始まりました").put("issueType", "LANGUAGE_FORM").put("sourceFactId", "").put("formReason", "INFLECTION");

        // 실행·검증: 실제 형태 교정과 선택적인 구두점을 구분한다.
        assertSame(value, WritingPlanContract.plan(value));
        correction.put("replacement", "始まります、");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        correction.put("replacement", "始まりました").put("formReason", "NONE");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        correction.put("formReason", "INFLECTION").put("sourceFactId", "T1");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

    @Test
    void missingRequirementCannotInventRelationEvidence() {
        // 준비
        var value = typedPlan();
        var observation = (ObjectNode) result(value).path("observations").get(0);
        observation.put("status", "UNMET").put("evidenceKind", "MISSING_REQUIREMENT");
        observation.putArray("segmentIds");
        observation.putArray("answerQuotes");
        var relations = observation.putObject("relationChecks");
        for (String key : new String[] {"causality", "chronology", "specificity", "stance"}) relations.put(key, "NOT_APPLICABLE");

        // 실행·검증
        assertSame(value, WritingPlanContract.plan(value));
        relations.put("stance", "CONFLICT");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

    @Test
    void v5ExpressionAlternativeRequiresPreservedRelations() {
        // 준비: 같은 시각을 표현만 바꾸는 제안은 원래 사실을 유지한다.
        var value = typedPlan();
        var result = result(value);
        result.put("policyVersion", "curated-writing-feedback-v5");
        var alternative = result.withArray("optionalAlternatives").addObject().put("suggestionKind", "EXPRESSION_ONLY")
                .put("segmentId", "S1").put("original", "午前九時に始まります。").put("replacement", "開始は午前九時です。");
        var relations = alternative.putObject("relationChecks").put("causality", "NOT_APPLICABLE")
                .put("chronology", "PRESERVED").put("specificity", "PRESERVED").put("stance", "NOT_APPLICABLE");

        // 실행·검증: 누락·알 수 없는 값·추가 사실·불확실은 표현만 바뀐 것으로 중계하지 않는다.
        assertSame(value, WritingPlanContract.plan(value));
        for (String invalid : new String[] {"", "UNKNOWN", "CONFLICT", "UNCERTAIN"}) {
            relations.put("specificity", invalid);
            assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        }
        relations.put("specificity", "PRESERVED");
        relations.remove("stance");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

    @Test
    void v5HypotheticalAlternativeKeepsItsExplicitClassification() {
        // 준비: 원문에 없는 조건은 표현 교정 대신 가정 예시로 분류되어 있다.
        var value = typedPlan();
        var result = result(value);
        result.put("policyVersion", "curated-writing-feedback-v5");
        var alternative = result.withArray("optionalAlternatives").addObject().put("suggestionKind", "HYPOTHETICAL_EXAMPLE")
                .put("segmentId", "S1").put("original", "午前九時に始まります。").put("replacement", "遅れる場合は午前十時に始まります。");
        var relations = alternative.putObject("relationChecks").put("causality", "UNCERTAIN")
                .put("chronology", "CONFLICT").put("specificity", "CONFLICT").put("stance", "NOT_APPLICABLE");

        // 실행·검증: 가정은 허용하지만 관계 근거의 필수 구조를 생략하지 않는다.
        assertSame(value, WritingPlanContract.plan(value));
        relations.remove("chronology");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

    @Test
    void storedV4AlternativesDoNotRequireNewV5Fields() {
        // 준비: 과거 v4 저장 결과를 새 계약으로 재작성하지 않는다.
        var value = typedPlan();
        var result = result(value);
        result.withArray("optionalAlternatives").addObject().put("suggestionKind", "EXPRESSION_ONLY")
                .put("segmentId", "S1").put("original", "午前九時に始まります。").put("replacement", "開始は午前九時です。");

        // 실행·검증
        assertSame(value, WritingPlanContract.plan(value));
        assertEquals("curated-writing-feedback-v4", result.path("policyVersion").asText());
        assertFalse(result.path("optionalAlternatives").get(0).has("relationChecks"));
    }

    private ObjectNode structuredPlan() {
        var value = typedPlan();
        var item = (ObjectNode) value.path("items").get(0);
        item.putArray("taskRequirements").add("시각 전달");
        result(value).put("policyVersion", "curated-writing-feedback-v6");
        var observation = (ObjectNode) result(value).path("observations").get(0);
        var part = observation.putArray("requirementParts").addObject().put("requirementQuote", "시각 전달").put("status", "PRESENT");
        part.putArray("evidence").addObject().put("segmentId", "S1").put("quote", "午前九時に始まります。");
        part.putArray("taskFactIds").add("T1");
        return value;
    }

    private ObjectNode structuredAlternative(ObjectNode value) {
        var alternative = result(value).withArray("optionalAlternatives").addObject().put("requirementId", "C1")
                .put("suggestionKind", "EXPRESSION_ONLY").put("segmentId", "S1")
                .put("original", "午前九時に始まります。").put("replacement", "九時に始まります。");
        alternative.putObject("patch").put("original", "午前").put("replacement", "");
        alternative.putObject("relationChecks").put("causality", "NOT_APPLICABLE").put("chronology", "PRESERVED")
                .put("specificity", "PRESERVED").put("stance", "NOT_APPLICABLE");
        return alternative;
    }

    private ObjectNode prescribedPlan() {
        var value = structuredPlan();
        value.put("writingType", "GUIDED");
        var item = (ObjectNode) value.path("items").get(0);
        item.putArray("requiredIntents").add("").add("원래 예정 시각임을 나타낸다.")
                .add("원래 예정 시각임을 나타낸다.").add("변경 결정을 나타낸다.");
        result(value).put("policyVersion", "curated-writing-feedback-v8");
        var part = (ObjectNode) result(value).path("observations").get(0).path("requirementParts").get(0);
        part.putArray("taskConditionIds").add("P1").add("P2");
        return value;
    }

    private ObjectNode conditionCorrection(ObjectNode value) {
        var observation = (ObjectNode) result(value).path("observations").get(0);
        observation.put("status", "UNMET");
        ((ObjectNode) observation.path("relationChecks")).put("chronology", "CONFLICT");
        ((ObjectNode) observation.path("requirementParts").get(0)).put("status", "CONFLICT");
        var correction = result(value).withArray("necessaryCorrections").addObject()
                .put("requirementId", "C1").put("segmentId", "S1")
                .put("original", "午前九時に始まります。").put("replacement", "午前九時に始まる予定でした。")
                .put("issueType", "TASK_CONDITION_CONFLICT").put("sourceFactId", "")
                .put("sourceAuthorityId", "P1").put("formReason", "NONE");
        correction.putObject("patch").put("original", "始まります").put("replacement", "始まる予定でした");
        return correction;
    }

    @Test
    void v8GuidedConditionAuthorityIsDistinctFromFactAndLanguageForm() {
        // 준비: 명시된 과제 지시는 실제 학습자 사실이나 활용 오류와 다르다.
        var value = prescribedPlan();
        var correction = conditionCorrection(value);

        // 실행·검증: 정상 조건 교정은 전달하고 권한 혼합은 거절한다.
        assertSame(value, WritingPlanContract.plan(value));
        correction.put("sourceFactId", "T1");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        correction.put("sourceFactId", "").put("formReason", "INFLECTION");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        correction.put("formReason", "NONE").put("sourceAuthorityId", "T1");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

    @Test
    void v9ServerAssembledResultStillRequiresExactPrescribedAuthority() {
        // 준비: v9 모델 출력이 아니라 LL 서버가 조립해 저장한 v8 호환 결과다.
        var value = prescribedPlan();
        result(value).put("policyVersion", "curated-writing-feedback-v9");
        conditionCorrection(value);

        // 실행·검증: 정상 결과를 중계하고 P 연결을 지운 결과는 그대로 거절한다.
        assertSame(value, WritingPlanContract.plan(value));
        var part = (ObjectNode) result(value).path("observations").get(0).path("requirementParts").get(0);
        part.putArray("taskConditionIds").add("P2");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

    @Test
    void oldAndNewSemanticFeedbackPreservesStoredResultAndExactPatchAuthority() {
        for (String version : new String[] {"curated-writing-feedback-v8", "curated-writing-feedback-v9", "curated-writing-feedback-v10", "curated-writing-feedback-v11", "curated-writing-feedback-v12", "curated-writing-feedback-v13"}) {
            // 준비: 의미 판정 버전이 달라도 저장 DTO의 기존 권한 경계를 유지한다.
            var value = prescribedPlan();
            result(value).put("policyVersion", version);
            var correction = conditionCorrection(value);
            var original = value.deepCopy();

            // 실행·검증: 조회는 결과를 바꾸지 않고 원문과 다른 부분 교정은 거절한다.
            assertSame(value, WritingPlanContract.plan(value));
            assertEquals(original, value);
            ((ObjectNode) correction.path("patch")).put("original", "答えにない引用");
            assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        }
    }

    @Test
    void v12FreePersonalContentDoesNotAcquirePrescribedAuthorityOrUnknownVersion() {
        // 준비: 필수 교정이 없는 자유 작문 충족 결과도 중계할 수 있어야 한다.
        var value = prescribedPlan();
        value.put("writingType", "FREE");
        result(value).put("policyVersion", "curated-writing-feedback-v12");
        var part = (ObjectNode) result(value).path("observations").get(0).path("requirementParts").get(0);
        part.putArray("taskConditionIds");

        // 실행·검증: 개인 선택에 P 권한을 붙이거나 알 수 없는 계약을 표시하지 않는다.
        assertSame(value, WritingPlanContract.plan(value));
        part.putArray("taskConditionIds").add("P1");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        part.putArray("taskConditionIds");
        result(value).put("policyVersion", "curated-writing-feedback-v15");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

    @Test
    void v13KeepsServerMeaningIdentityAndRejectsMalformedCoverageOrMissingAuthority() {
        // 준비: 새 의미 계약의 결과도 기존 구조와 공개 원문을 그대로 중계한다.
        var value = prescribedPlan();
        var result = result(value);
        result.put("policyVersion", "curated-writing-feedback-v13")
                .put("meaningContractId", "authored-task-v1")
                .put("meaningContractVersion", "curated-writing-meaning-contract-v1")
                .put("meaningContractHash", "a".repeat(64));
        var original = value.deepCopy();
        var part = (ObjectNode) result.path("observations").get(0).path("requirementParts").get(0);

        // 실행·검증: 조회가 metadata를 바꾸지 않고 원문 coverage와 P 권한 검증을 적용한다.
        assertSame(value, WritingPlanContract.plan(value));
        assertEquals(original, value);
        part.put("requirementQuote", "시각");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        part.put("requirementQuote", "시각 전달");
        part.putArray("taskConditionIds").add("P1");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

    private ObjectNode serverRepairPlan() {
        var value = prescribedPlan();
        result(value).put("policyVersion", "curated-writing-feedback-v14")
                .put("meaningContractId", "authored-task-v1")
                .put("meaningContractVersion", "curated-writing-meaning-contract-v1")
                .put("meaningContractHash", "a".repeat(64))
                .put("repairPlanVersion", "server-repair-plan-v1")
                .put("repairPlanHash", "b".repeat(64))
                .put("optionalSuggestionsStatus", "SUCCEEDED")
                .putNull("optionalSuggestionsFailureCode");
        return value;
    }

    @Test
    void v14SeparatesOptionalFailureFromValidatedCoreWithoutRewritingStoredResult() {
        for (String status : new String[] {"SUCCEEDED", "FAILED", "NOT_RUN_LIMIT", "NOT_APPLICABLE"}) {
            // 준비: 선택 제안이 없어도 검증된 필수 코칭과 identity는 유지한다.
            var value = serverRepairPlan();
            var feedback = result(value);
            feedback.put("optionalSuggestionsStatus", status);
            if (Set.of("FAILED", "NOT_RUN_LIMIT").contains(status)) {
                feedback.put("optionalSuggestionsFailureCode", "OPTIONAL_UNAVAILABLE");
            }
            var original = value.deepCopy();

            // 실행 / 검증: 상태를 성공으로 고쳐 쓰거나 기존 코칭을 삭제하지 않는다.
            assertSame(value, WritingPlanContract.plan(value));
            assertEquals(original, value);
        }
    }

    @Test
    void v14RejectsMissingIdentityInvalidOptionalStateAndLeakedFailedSuggestion() {
        // 준비 / 실행 / 검증: 새 상태 분리에서도 기존 권한과 근거 검증을 모두 적용한다.
        for (String key : new String[] {"repairPlanHash", "repairPlanVersion", "meaningContractHash",
                "optionalSuggestionsStatus", "optionalSuggestionsFailureCode"}) {
            var value = serverRepairPlan();
            result(value).remove(key);
            assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        }
        var value = serverRepairPlan();
        result(value).put("optionalSuggestionsStatus", "FAILED");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        result(value).put("optionalSuggestionsFailureCode", "OPTIONAL_UNAVAILABLE");
        result(value).withArray("optionalAlternatives").addObject();
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

    @Test
    void v8ConditionIdsUseDistinctNonblankGuidedIntentsAndRejectUnknownOrDuplicateIds() {
        // 준비: 빈 항목·중복을 제거한 실제 P1/P2만 유효하다.
        var value = prescribedPlan();
        var part = (ObjectNode) result(value).path("observations").get(0).path("requirementParts").get(0);
        part.putArray("taskConditionIds").add("P1").add("P2");
        assertSame(value, WritingPlanContract.plan(value));

        // 검증: 모델이 새 ID를 만들거나 같은 권한을 중복 배정할 수 없다.
        for (String invalid : new String[] {"P0", "P3", "T1", "C1", ""}) {
            part.putArray("taskConditionIds").add(invalid);
            assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        }
        part.putArray("taskConditionIds").add("P1").add("P1");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        part.putArray("taskConditionIds").add("P2");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        part.remove("taskConditionIds");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

    @Test
    void v8FreeAndTranslationHaveNoPrescribedConditionAuthority() {
        // 준비: 공개 requiredIntents가 있어도 FREE의 개인 선택을 대신 고칠 권한은 없다.
        for (String type : new String[] {"FREE", "TRANSLATION"}) {
            var value = prescribedPlan();
            value.put("writingType", type);
            conditionCorrection(value);
            assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));

            // 실행·검증: P 연결을 지운 단순 관찰은 기존과 같이 조회한다.
            result(value).withArray("necessaryCorrections").removeAll();
            ((ObjectNode) result(value).path("observations").get(0).path("requirementParts").get(0)).putArray("taskConditionIds");
            assertSame(value, WritingPlanContract.plan(value));
        }
    }

    @Test
    void v8ConditionPatchUsesOnlyTheSameConflictingPartEvidence() {
        // 준비: 정상 시각과 잘못된 현재/과거 표현이 같은 문장에 있어도 근거를 섞지 않는다.
        var value = prescribedPlan();
        var correction = conditionCorrection(value);
        var observation = (ObjectNode) result(value).path("observations").get(0);
        var first = (ObjectNode) observation.path("requirementParts").get(0);
        first.put("requirementQuote", "시각 ").put("status", "PRESENT");
        first.putArray("taskConditionIds").add("P2");
        first.putArray("evidence").addObject().put("segmentId", "S1").put("quote", "午前九時");
        var conflict = observation.withArray("requirementParts").addObject().put("requirementQuote", "전달").put("status", "CONFLICT");
        conflict.putArray("taskFactIds");
        conflict.putArray("taskConditionIds").add("P1");
        conflict.putArray("evidence").addObject().put("segmentId", "S1").put("quote", "始まります");
        assertSame(value, WritingPlanContract.plan(value));

        // 검증: 다른 유효 P나 같은 문장의 정상 부분은 이 교정의 근거가 아니다.
        correction.put("sourceAuthorityId", "P2");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        correction.put("sourceAuthorityId", "P1").put("replacement", "午後十時に始まります。");
        correction.putObject("patch").put("original", "午前九時").put("replacement", "午後十時");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

    @Test
    void v8LanguageAndFactCorrectionsCannotBorrowConditionAuthority() {
        // 준비: v8의 새 ID 필드는 구형 언어/사실 교정에서는 명시적으로 비어 있어야 한다.
        var value = prescribedPlan();
        var correction = conditionCorrection(value);
        correction.put("issueType", "TASK_FACT_CONFLICT").put("sourceFactId", "T1").put("sourceAuthorityId", "");
        assertSame(value, WritingPlanContract.plan(value));
        correction.put("sourceAuthorityId", "P1");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        correction.put("issueType", "LANGUAGE_FORM").put("sourceFactId", "").put("sourceAuthorityId", "").put("formReason", "INFLECTION");
        assertSame(value, WritingPlanContract.plan(value));
        correction.remove("sourceAuthorityId");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

    @Test
    void v8PartialMissingCannotBecomeMetOrAuthorizeMissingContentCorrection() {
        // 준비: 실제 표현 근거를 유지하면서 요구의 다른 부분은 누락되어 있다.
        var value = prescribedPlan();
        var observation = (ObjectNode) result(value).path("observations").get(0);
        observation.put("status", "UNMET");
        var present = (ObjectNode) observation.path("requirementParts").get(0);
        present.put("requirementQuote", "시각 ").putArray("taskConditionIds").add("P2");
        var absent = observation.withArray("requirementParts").addObject().put("requirementQuote", "전달").put("status", "ABSENT");
        absent.putArray("evidence");
        absent.putArray("taskFactIds");
        absent.putArray("taskConditionIds").add("P1");
        assertSame(value, WritingPlanContract.plan(value));

        // 검증: 누락을 충족으로 바꾸거나 다른 부분의 인용으로 내용 보충을 강제하지 않는다.
        observation.put("status", "MET");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        observation.put("status", "UNMET");
        conditionCorrection(value);
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

    @Test
    void v8StillRequiresExactServerRestoredCoverageAndExistingObservationIds() {
        // 준비·실행: 모델 원문 복사를 없애도 저장 결과의 실제 원문 coverage는 그대로다.
        var value = prescribedPlan();
        assertSame(value, WritingPlanContract.plan(value));
        var observation = (ObjectNode) result(value).path("observations").get(0);
        var part = (ObjectNode) observation.path("requirementParts").get(0);

        // 검증: 잘못 복원한 문자열이나 존재하지 않는 요구 ID를 중계하지 않는다.
        part.put("requirementQuote", "시각을 전달함");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        part.put("requirementQuote", "시각 전달");
        observation.put("requirementId", "C9");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

    @Test
    void v6PartsRequireCompleteRequirementAndActualEvidence() {
        // 준비·실행: 공개 조건 전체와 학습자 원문에 연결된 요소를 그대로 읽는다.
        var value = structuredPlan();
        var part = (ObjectNode) result(value).path("observations").get(0).path("requirementParts").get(0);
        assertSame(value, WritingPlanContract.plan(value));

        // 검증: 조건 일부 누락·발명한 인용·근거 없는 PRESENT를 허용하지 않는다.
        part.put("requirementQuote", "시각");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        part.put("requirementQuote", "시각 전달");
        ((ObjectNode) part.path("evidence").get(0)).put("quote", "午後十時");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        part.putArray("evidence");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

    @Test
    void v7ReadsRestoredSourceCoverageWithoutRelaxingExactPublicQuotes() {
        // 준비: LL이 경계 공백을 원문 그대로 복원한 새 저장 계약.
        var value = structuredPlan();
        result(value).put("policyVersion", "curated-writing-feedback-v7");
        var observation = (ObjectNode) result(value).path("observations").get(0);
        var first = (ObjectNode) observation.path("requirementParts").get(0);
        first.put("requirementQuote", "시각 ");
        var second = observation.withArray("requirementParts").addObject().put("requirementQuote", "전달").put("status", "PRESENT");
        second.putArray("evidence").addObject().put("segmentId", "S1").put("quote", "始まります");
        second.putArray("taskFactIds");

        // 실행·검증: 공개 원문의 정확한 공백·문자를 요구하며 새 버전에서도 누락·추가·중복은 거절한다.
        assertSame(value, WritingPlanContract.plan(value));
        for (String invalid : new String[] {"시각", "시각x ", "시각 시각 "}) {
            first.put("requirementQuote", invalid);
            assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        }
        first.put("requirementQuote", "시각 ");
        assertSame(value, WritingPlanContract.plan(value));
    }

    @Test
    void v7KeepsSinglePatchAndOptionalRelationChecks() {
        // 준비: 새 정책 식별자에서도 기존 v6의 조립 패치와 관계 계약을 사용한다.
        var value = structuredPlan();
        result(value).put("policyVersion", "curated-writing-feedback-v7");
        var alternative = structuredAlternative(value);

        // 실행·검증: 정상 표현은 유지하고 패치 밖의 변경·의미 충돌은 거절한다.
        assertSame(value, WritingPlanContract.plan(value));
        alternative.put("replacement", "九時に終わります。");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        alternative.put("replacement", "九時に始まります。");
        ((ObjectNode) alternative.path("relationChecks")).put("stance", "CONFLICT");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

    @Test
    void v6PartialMissingRetainsPresentOrUncertainPartEvidence() {
        // 준비: 조건 일부는 답안에 있고 다른 일부는 없다. 실제 부분의 인용과 누락을 구분한다.
        var value = structuredPlan();
        var observation = (ObjectNode) result(value).path("observations").get(0);
        observation.put("status", "UNMET").put("evidenceKind", "LEARNER_TEXT");
        var first = (ObjectNode) observation.path("requirementParts").get(0);
        first.put("requirementQuote", "시각 ");
        var absent = observation.withArray("requirementParts").addObject().put("requirementQuote", "전달").put("status", "ABSENT");
        absent.putArray("evidence");
        absent.putArray("taskFactIds");

        // 실행·검증: 누락 우선 집계와 부분 불확실을 v4 규칙으로 잘못 거절하지 않는다.
        assertSame(value, WritingPlanContract.plan(value));
        observation.put("status", "MET");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        observation.put("status", "UNMET");
        first.put("status", "UNCERTAIN");
        ((ObjectNode) observation.path("relationChecks")).put("chronology", "UNCERTAIN");
        assertSame(value, WritingPlanContract.plan(value));
        ((ObjectNode) observation.path("relationChecks")).put("chronology", "PRESERVED");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

    @Test
    void v6AllAbsentHasNoLearnerQuote() {
        // 준비: 모든 요소가 없을 때만 전체 관찰도 누락으로 표시한다.
        var value = structuredPlan();
        var observation = (ObjectNode) result(value).path("observations").get(0);
        observation.put("status", "UNMET").put("evidenceKind", "MISSING_REQUIREMENT");
        observation.putArray("segmentIds");
        observation.putArray("answerQuotes");
        var part = (ObjectNode) observation.path("requirementParts").get(0);
        part.put("status", "ABSENT").putArray("evidence");
        var relations = observation.putObject("relationChecks");
        for (String key : new String[] {"causality", "chronology", "specificity", "stance"}) relations.put(key, "NOT_APPLICABLE");

        // 실행·검증
        assertSame(value, WritingPlanContract.plan(value));
        observation.withArray("answerQuotes").add("午前九時に始まります。");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

    @Test
    void v6PatchPreservesOutsideTextAndAllowsDeletion() {
        // 준비·실행: 삭제 패치도 나머지 원문이 같으면 표시할 수 있다.
        var value = structuredPlan();
        var alternative = structuredAlternative(value);
        assertSame(value, WritingPlanContract.plan(value));

        // 검증: 패치 밖 의미 변경과 누락된 patch는 거절한다.
        alternative.put("replacement", "九時に終わります。");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        alternative.put("replacement", "九時に始まります。");
        alternative.remove("patch");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

    @Test
    void v6FactPatchRequiresTheConflictingFactPartEvidence() {
        // 준비: 같은 문장 안에서 시각은 충돌하지만 동작 표현은 정상이다.
        var value = structuredPlan();
        var observation = (ObjectNode) result(value).path("observations").get(0);
        observation.put("status", "UNMET");
        ((ObjectNode) observation.path("relationChecks")).put("chronology", "CONFLICT");
        var parts = observation.putArray("requirementParts");
        var conflict = parts.addObject().put("requirementQuote", "시각 ").put("status", "CONFLICT");
        conflict.putArray("evidence").addObject().put("segmentId", "S1").put("quote", "午前九時");
        conflict.putArray("taskFactIds").add("T1");
        var present = parts.addObject().put("requirementQuote", "전달").put("status", "PRESENT");
        present.putArray("evidence").addObject().put("segmentId", "S1").put("quote", "始まります");
        present.putArray("taskFactIds").add("T2");
        var correction = result(value).withArray("necessaryCorrections").addObject().put("requirementId", "C1").put("segmentId", "S1")
                .put("original", "午前九時に始まります。").put("replacement", "午後十時に始まります。")
                .put("issueType", "TASK_FACT_CONFLICT").put("sourceFactId", "T1").put("formReason", "NONE")
                .put("originText", "주어진 시각을 확인하세요.").put("learningText", "指定時刻を確認してください。");
        correction.putObject("patch").put("original", "午前九時").put("replacement", "午後十時");

        // 실행·검증: 명시된 충돌 사실의 인용만 교정할 수 있다.
        assertSame(value, WritingPlanContract.plan(value));
        correction.put("sourceFactId", "T2");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        correction.put("sourceFactId", "T1").put("replacement", "午前九時に始まりました。");
        correction.putObject("patch").put("original", "始まります").put("replacement", "始まりました");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

    @Test
    void v6PatchCannotChooseAnAmbiguousRepeatedSource() {
        // 준비: 같은 원문 부분이 두 번 있으면 조립 위치가 모호하다.
        var value = structuredPlan();
        String answer = "午前九時に始まります。午前九時に始まります。";
        ((ObjectNode) value.path("items").get(0).path("attempts").get(0)).put("answer", answer);
        ((ObjectNode) result(value).path("observations").get(0)).putArray("answerQuotes").add(answer);
        var alternative = structuredAlternative(value);
        alternative.put("original", answer).put("replacement", "九時に始まります。午前九時に始まります。");

        // 실행·검증
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

    @Test
    void v6ExpressionCannotCompeteWithRequiredCorrectionOrRewriteUnmetRequirement() {
        // 준비: 한 구간에 필수 교정과 선택 표현을 동시에 제안한 응답.
        var value = structuredPlan();
        var alternative = structuredAlternative(value);
        var correction = result(value).withArray("necessaryCorrections").addObject().put("requirementId", "C1").put("segmentId", "S1")
                .put("original", "午前九時に始まります。").put("replacement", "午前九時に始まりました。")
                .put("issueType", "LANGUAGE_FORM").put("sourceFactId", "").put("formReason", "INFLECTION");
        correction.putObject("patch").put("original", "始まります").put("replacement", "始まりました");

        // 실행·검증: 충돌하는 action은 거절하고, 가상 예시는 MET 강제 없이 별도 분류로 유지한다.
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        result(value).withArray("optionalAlternatives").removeAll();
        assertSame(value, WritingPlanContract.plan(value));
        result(value).withArray("necessaryCorrections").removeAll();
        result(value).withArray("optionalAlternatives").add(alternative);
        var observation = (ObjectNode) result(value).path("observations").get(0);
        observation.put("status", "UNMET");
        ((ObjectNode) observation.path("requirementParts").get(0)).put("status", "CONFLICT");
        ((ObjectNode) observation.path("relationChecks")).put("chronology", "CONFLICT");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        alternative.put("suggestionKind", "HYPOTHETICAL_EXAMPLE");
        assertSame(value, WritingPlanContract.plan(value));
        alternative.put("requirementId", "C9");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

    @Test
    void v14ReadsActualN01ContiguousConflictQuotesWithoutRewritingStoredPlan() {
        // 준비: 동결 N01의 원문/공개 요구와 실제 LL DB 성공 결과를 그대로 조합한 테스트 envelope다.
        var value = compositeConflictPlan();
        var before = value.deepCopy();
        assertEquals(2, compositeConflictPart(value).path("evidence").size());
        assertEquals("できあがった鉢は図書館の展示棚に置き、何も持たずに帰りました。",
                compositeCorrection(value).path("patch").path("original").asText());

        // 실행 / 검증: 같은 T4의 같은 CONFLICT part에 정확히 이어지는 두 인용만 하나의 수리 범위를 지지한다.
        assertSame(value, WritingPlanContract.plan(value));
        assertEquals(before, value);
    }

    @Test
    void v14CompositeConditionUsesTheSameConflictingPartAuthority() {
        // 준비: 동일한 원문 범위를 실제 연결된 P2 조건 권한으로 고치는 경우도 같은 인용 경계를 쓴다.
        var value = compositeConflictPlan();
        compositeCorrection(value).put("issueType", "TASK_CONDITION_CONFLICT")
                .put("sourceFactId", "").put("sourceAuthorityId", "P2");

        // 실행 / 검증: P2는 통과하지만 다른 part의 유효 P1은 이 범위의 권한이 아니다.
        assertSame(value, WritingPlanContract.plan(value));
        compositeCorrection(value).put("sourceAuthorityId", "P1");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

    @Test
    void v14CompositeQuotesRejectAnyGapIncludingPunctuation() {
        // 준비: 한 글자의 공백이나 문장부호 누락도 연속 근거로 대신하지 않는다.
        for (boolean punctuation : new boolean[] {false, true}) {
            var value = compositeConflictPlan();
            var evidence = compositeConflictPart(value).withArray("evidence");
            if (punctuation) {
                var first = (ObjectNode) evidence.get(0);
                String quote = first.path("quote").asText();
                first.put("quote", quote.substring(0, quote.length() - 1));
            } else {
                var second = (ObjectNode) evidence.get(1);
                second.put("quote", second.path("quote").asText().substring(1));
            }

            // 실행 / 검증: 인용을 단순 이어 붙이거나 문장부호를 무시해 틈을 메우지 않는다.
            assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        }
    }

    @Test
    void v14CompositeQuotesCannotBorrowAnotherPartSegmentOrFact() {
        // 준비 / 실행 / 검증: 같은 문장이나 과제에 존재하는 근거라도 part/문장/권한이 다르면 사용하지 않는다.
        var wrongSegment = compositeConflictPlan();
        ((ObjectNode) compositeConflictPart(wrongSegment).path("evidence").get(1)).put("segmentId", "S1");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(wrongSegment));

        var wrongFact = compositeConflictPlan();
        compositeCorrection(wrongFact).put("sourceFactId", "T3");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(wrongFact));

        var crossPart = compositeConflictPlan();
        var conflictingEvidence = compositeConflictPart(crossPart).withArray("evidence");
        var removed = conflictingEvidence.remove(1);
        ((ObjectNode) compositeObservation(crossPart).path("requirementParts").get(0)).withArray("evidence").add(removed);
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(crossPart));
    }

    @Test
    void v14CompositeQuotesRejectDuplicateAmbiguousAndOverlappingAnchors() {
        // 준비: 같은 위치를 중복 기재하거나 서로 겹치는 인용으로 연속 coverage를 만들 수 없다.
        var duplicate = compositeConflictPlan();
        var duplicateEvidence = compositeConflictPart(duplicate).withArray("evidence");
        duplicateEvidence.add(duplicateEvidence.get(0).deepCopy());
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(duplicate));

        var overlap = compositeConflictPlan();
        var overlapEvidence = compositeConflictPart(overlap).withArray("evidence");
        String first = overlapEvidence.get(0).path("quote").asText();
        String second = overlapEvidence.get(1).path("quote").asText();
        ((ObjectNode) overlapEvidence.get(1)).put("quote", first.substring(first.length() - 1) + second);
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(overlap));

        // 실행 / 검증: patch 전체는 유일해도 내부 인용의 실제 위치가 둘이면 수용하지 않는다.
        var ambiguous = compositeConflictPlan();
        var evidence = compositeConflictPart(ambiguous).withArray("evidence");
        String repeated = evidence.get(0).path("quote").asText();
        String oldSegment = compositeCorrection(ambiguous).path("original").asText();
        String expanded = repeated + oldSegment;
        var attempt = (ObjectNode) ambiguous.path("items").get(0).path("attempts").get(0);
        attempt.put("answer", attempt.path("answer").asText().replace(oldSegment, expanded));
        compositeObservation(ambiguous).withArray("answerQuotes").set(1, json.getNodeFactory().textNode(expanded));
        compositeCorrection(ambiguous).put("original", expanded);
        ((ObjectNode) compositeCorrection(ambiguous).path("patch")).put("original", expanded);
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(ambiguous));
    }

    @Test
    void v14CompositeQuotesCannotEscapeFixedPatchOrSkipFullSegmentAndPatchChecks() {
        // 준비: 실제 patch가 첫 글자 뒤에서 시작하면 그 밖의 인용 전체를 합쳐 권한을 넓히지 않는다.
        var escaped = compositeConflictPlan();
        var correction = compositeCorrection(escaped);
        ((ObjectNode) correction.path("patch")).put("original", correction.path("original").asText().substring(1))
                .put("replacement", correction.path("replacement").asText().substring(1));
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(escaped));

        // 실행 / 검증: 기존 전체 문장 동일성과 patch 재조립 검사는 fallback 뒤에도 그대로 적용한다.
        var wrongPatch = compositeConflictPlan();
        compositeCorrection(wrongPatch).put("replacement", "別の内容です。");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(wrongPatch));
        var wrongFullSegment = compositeConflictPlan();
        compositeObservation(wrongFullSegment).withArray("answerQuotes").set(1,
                json.getNodeFactory().textNode(compositeCorrection(wrongFullSegment).path("original").asText() + "追加。"));
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(wrongFullSegment));
    }

    @Test
    void compositeFallbackDoesNotApplyToV13OrLanguageFormAndSingleQuoteStillWorks() {
        // 준비: 새 연결 허용은 v14의 TASK_FACT/CONDITION에만 한정한다.
        for (String version : new String[] {"curated-writing-feedback-v8", "curated-writing-feedback-v12", "curated-writing-feedback-v13"}) {
            var value = compositeConflictPlan();
            result(value).put("policyVersion", version);
            assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
        }
        var language = compositeConflictPlan();
        compositeCorrection(language).put("issueType", "LANGUAGE_FORM").put("sourceFactId", "")
                .put("sourceAuthorityId", "").put("formReason", "INFLECTION");
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(language));

        // 실행 / 검증: 기존 단일 인용 LF 경로는 그대로 통과한다.
        compositeConflictPart(language).putArray("evidence").addObject().put("segmentId", "S3")
                .put("quote", compositeCorrection(language).path("original").asText());
        assertSame(language, WritingPlanContract.plan(language));
    }

    private ObjectNode compositeConflictPlan() {
        // 원문/요구는 동결 N01, result는 실제 LL 성공 원본이다. owner·auth·운영 metadata는 fixture에 없다.
        try (var source = getClass().getResourceAsStream("/writing/v14-n01-composite-feedback.json")) {
            assertNotNull(source);
            var fixture = json.readTree(source);
            var value = plan(1, 1);
            value.put("personalizedFeedbackStatus", "ACTIVE_QA").put("submittedItemCount", 1)
                    .put("feedbackCompletedItemCount", 1).put("status", "COMPLETED").put("writingType", "GUIDED");
            var item = (ObjectNode) value.path("items").get(0);
            item.put("answered", true);
            item.set("taskRequirements", fixture.path("taskRequirements").deepCopy());
            item.set("requiredIntents", fixture.path("requiredIntents").deepCopy());
            item.putObject("reference").set("checklist", fixture.path("taskRequirements").deepCopy());
            var attempt = item.withArray("attempts").addObject().put("answer", fixture.path("answer").asText())
                    .put("resultPolicy", "REFERENCE_ONLY").putNull("evaluation");
            attempt.putObject("feedback").put("status", "SUCCEEDED").put("canRetry", false)
                    .putNull("failureCode").set("result", fixture.path("result").deepCopy());
            return value;
        } catch (java.io.IOException failure) {
            throw new AssertionError("Controlled actual N01 public feedback fixture cannot be read", failure);
        }
    }

    private ObjectNode compositeObservation(ObjectNode value) {
        for (var observation : result(value).path("observations")) {
            if ("C4".equals(observation.path("requirementId").asText())) return (ObjectNode) observation;
        }
        throw new AssertionError("Actual N01 C4 observation is missing");
    }

    private ObjectNode compositeConflictPart(ObjectNode value) {
        for (var part : compositeObservation(value).path("requirementParts")) {
            if ("CONFLICT".equals(part.path("status").asText())) return (ObjectNode) part;
        }
        throw new AssertionError("Actual N01 conflicting part is missing");
    }

    private ObjectNode compositeCorrection(ObjectNode value) {
        return (ObjectNode) result(value).path("necessaryCorrections").get(0);
    }

    @Test
    void v14CompositeFallbackRejectsAnAdditionalActualQuoteFromAnotherSegmentInTheSamePart() {
        // 준비: S3 두 절은 완전히 이어져 있어도 같은 CONFLICT part에 S1 인용을 더해 범위를 섞지 않는다.
        var value = compositeConflictPlan();
        var actualOtherSegment = compositeObservation(value).path("requirementParts").get(0)
                .path("evidence").get(0).deepCopy();
        assertEquals("S1", actualOtherSegment.path("segmentId").asText());
        compositeConflictPart(value).withArray("evidence").add(actualOtherSegment);

        // 실행 / 검증: 다른 문장을 무시해서 앞선 S3 coverage만으로 수용하지 않는다.
        assertThrows(LanguageLearningServiceException.class, () -> WritingPlanContract.plan(value));
    }

}
