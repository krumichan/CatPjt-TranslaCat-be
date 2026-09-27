package jp.co.translacat.infrastructure.languagelearning.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingType;
import jp.co.translacat.domain.languagelearning.daily.dto.request.AnswerSubmitRequestDto;
import jp.co.translacat.infrastructure.languagelearning.client.config.LanguageLearningClientProperties;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LanguageLearningWritingClientTest {
    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://ll.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final LanguageLearningWritingClient client = client();

    private LanguageLearningWritingClient client() {
        var properties = new LanguageLearningClientProperties();
        properties.getInternalJwt().setSecretBase64(Base64.getEncoder().encodeToString(new byte[32]));
        return new LanguageLearningWritingClient(builder.build(),
                new LanguageLearningInternalJwtProvider(properties, Clock.systemUTC()),
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void createSendsOnlyModeToKtor() {
        // 준비: 날짜와 생성 snapshot은 LL에서만 준비한다.
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/writing/daily/sets"))
                .andExpect(content().json("{\"writingType\":\"FREE\"}", true))
                .andRespond(withSuccess("""
                        {"dailySetId":7,"learningDate":"2026-09-26","writingType":"FREE","snapshotId":"snapshot",
                        "status":"GENERATING","sentenceCount":1,"generatedItemCount":0,
                        "generationFailureMessage":null,"regenerationCount":0,"promptVersion":null,
                        "reviewAvailable":true,"items":[],"regenerating":false}
                        """, MediaType.APPLICATION_JSON));

        // 실행: BE의 실제 HTTP 변환기로 생성 요청을 보낸다.
        var result = client.create(123L, DailyWritingType.FREE);

        // 검증: 날짜와 응답 세트 계약을 모두 유지한다.
        assertEquals(7L, result.dailySetId());
        server.verify();
    }

    @Test
    void readConvertsLlResponseToExistingBeDto() {
        // 준비: 실제 LL 응답의 세트·문항·답변 필드를 포함한다.
        server.expect(requestTo(
                        "http://ll.test/internal/v1/language-learning/writing/daily/history/2026-09-26?writingType=FREE"))
                .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Bearer ")))
                .andRespond(withSuccess("""
                        {"dailySetId":7,"learningDate":"2026-09-26","writingType":"FREE","snapshotId":"snapshot",
                        "status":"READY","sentenceCount":1,"generatedItemCount":1,"generationFailureMessage":null,
                        "regenerationCount":0,"promptVersion":"v1","reviewAvailable":true,"regenerating":false,
                        "items":[{"itemId":11,"order":1,"difficulty":"NORMAL","originText":"합성 문장",
                        "keywords":[],"focusMetrics":[],"focusReason":"합성 이유","providedFacts":[],
                        "requiredIntents":[],"responseConstraints":[],"answered":false,"answeredToday":false,
                        "canSubmit":true,"attempts":[],"contentRevision":"revision"}]}
                        """, MediaType.APPLICATION_JSON));

        // 실행: 기존 외부 DTO로 읽어 FE가 사용하는 필드를 보존한다.
        var result = client.history(123L, LocalDate.of(2026, 9, 26), DailyWritingType.FREE);

        // 검증: LL 소유 ID와 수정 보호 revision이 손실되지 않는다.
        assertEquals(7L, result.dailySetId());
        assertEquals(11L, result.items().getFirst().itemId());
        assertEquals("revision", result.items().getFirst().contentRevision());
        server.verify();
    }

    @Test
    void submitForwardsRevisionAndDoesNotRetryConflict() {
        // 준비: LL이 답변 중복을 거부한다.
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/writing/daily/items/11/answers"))
                .andExpect(content().json("{\"answer\":\"합성 답변\",\"contentRevision\":\"rev-1\"}"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"LANGUAGE_LEARNING_ANSWER_NOT_ALLOWED\",\"message\":\"답변 불가\"}"));

        // 실행: 외부 BE 호출을 LL에 한 번 전달한다.
        var failure = assertThrows(LanguageLearningServiceException.class,
                () -> client.submit(123L, 11L, new AnswerSubmitRequestDto("합성 답변", "rev-1")));

        // 검증: 정당한 거부를 provider 오류나 재시도 대상으로 바꾸지 않는다.
        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatus());
        assertEquals("LANGUAGE_LEARNING_ANSWER_NOT_ALLOWED", failure.getErrorCode());
        server.verify();
    }

    @Test
    void malformedSuccessIsProtocolFailure() {
        // 준비: LL 성공 응답의 필수 필드가 빠진다.
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/writing/daily/sets/7"))
                .andRespond(withSuccess("{\"dailySetId\":7}", MediaType.APPLICATION_JSON));

        // 실행: 응답을 DTO로 파싱한다.
        var failure = assertThrows(LanguageLearningServiceException.class, () -> client.get(123L, 7L));

        // 검증: 잘못된 프로토콜을 성공 세트로 전달하지 않는다.
        assertEquals("LL_WRITING_CONTRACT_ERROR", failure.getErrorCode());
        server.verify();
    }
}
