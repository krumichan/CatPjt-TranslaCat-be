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
}
