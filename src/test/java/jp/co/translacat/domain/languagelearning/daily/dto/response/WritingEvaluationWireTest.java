package jp.co.translacat.domain.languagelearning.daily.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WritingEvaluationWireTest {
    @Test
    void bilingualMessagesAndCorrectionsRemainInTheExternalEvaluationContract() throws Exception {
        // 준비: 내부 AI DTO와 별개로 현재 외부 평가 응답에서 쓰는 두 타입을 검사한다.
        var mapper = new ObjectMapper().findAndRegisterModules();
        String body = """
                {"evaluationId":-1,"context":"DAILY","overall":80,"meaning":80,"grammar":80,
                 "vocabulary":80,"naturalness":80,"expression":80,
                 "strengths":[{"originText":"합성 장점","learningText":"Synthetic strength"}],
                 "weaknesses":[],"corrections":[{"original":"I goes.","corrected":"I go.","category":"GRAMMAR",
                   "explanation":{"originText":"합성 설명","learningText":"Synthetic explanation"}}],
                 "recommendedAnswers":["I go."],"explanation":{"originText":"합성 종합","learningText":"Synthetic summary"},
                 "evaluationRubricVersion":"test","scoringPolicyVersion":"test","promptVersion":"test",
                 "evaluatedAt":"2026-09-27T00:00:00"}
                """;

        // 실행
        var value = mapper.readValue(body, WritingEvaluationResponseDto.class);
        var wire = mapper.valueToTree(value);

        // 검증: 중첩 필드 이름과 원문·학습 언어 값이 그대로 왕복한다.
        assertEquals("합성 장점", value.strengths().getFirst().originText());
        assertEquals("I go.", value.corrections().getFirst().corrected());
        assertEquals("Synthetic explanation", wire.at("/corrections/0/explanation/learningText").asText());
        assertEquals("합성 종합", wire.at("/explanation/originText").asText());
    }
}
