package jp.co.translacat.infrastructure.languagelearning.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.practice.dto.request.PracticeAnswerSubmitRequestDto;
import jp.co.translacat.infrastructure.languagelearning.client.config.LanguageLearningClientProperties;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LanguageLearningPracticeClientTest {
    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://ll.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final LanguageLearningPracticeClient client = client();

    private LanguageLearningPracticeClient client() {
        var properties = new LanguageLearningClientProperties();
        properties.getInternalJwt().setSecretBase64(Base64.getEncoder().encodeToString(new byte[32]));
        return new LanguageLearningPracticeClient(builder.build(),
                new LanguageLearningInternalJwtProvider(properties, Clock.systemUTC()),
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void todayForwardsOnlyModeAndKeepsPendingContract() {
        // 준비: 날짜·밴드·키워드·문항 계획은 LL이 준비한다.
        server.expect(requestTo(
                        "http://ll.test/internal/v1/language-learning/practice/today?domain=READING&mode=COMPREHENSION"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Bearer ")))
                .andRespond(withSuccess("""
                        {"practiceSetId":-1,"learningDate":"2026-09-26","domain":"READING",
                         "mode":"COMPREHENSION","status":"ACTIVE","questionCount":5,"answeredCount":0,
                         "correctCount":0,"officialScore":null,"complexityBand":3,"promptVersion":null,
                         "metrics":[],"questions":[],"generationStatus":"PENDING","generatedQuestionCount":0,
                         "generationFailureMessage":null}
                        """, MediaType.APPLICATION_JSON));

        // 실행
        var result = client.today(101L, PracticeDomain.READING, "COMPREHENSION");

        // 검증
        assertEquals(-1L, result.practiceSetId());
        assertEquals(5, result.questionCount());
        assertEquals("PENDING", result.generationStatus().name());
        server.verify();
    }

    @Test
    void answerPreservesOriginalBusinessFailureWithoutRetry() {
        // 준비
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/practice/questions/-12/answers"))
                .andExpect(content().json("{\"answer\":[\"A\"]}", true))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"LANGUAGE_LEARNING_ANSWER_NOT_ALLOWED\",\"message\":\"답변 불가\"}"));

        // 실행
        var error = assertThrows(LanguageLearningServiceException.class,
                () -> client.answer(101L, -12L, new PracticeAnswerSubmitRequestDto(List.of("A"))));

        // 검증
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("LANGUAGE_LEARNING_ANSWER_NOT_ALLOWED", error.getErrorCode());
        server.verify();
    }

    @Test
    void malformedSuccessIsAContractFailure() {
        // 준비
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/practice/sets/-1"))
                .andRespond(withSuccess("{\"practiceSetId\":-1}", MediaType.APPLICATION_JSON));

        // 실행
        var error = assertThrows(LanguageLearningServiceException.class, () -> client.get(101L, -1L));

        // 검증
        assertEquals("LL_PRACTICE_CONTRACT_ERROR", error.getErrorCode());
        server.verify();
    }
}
