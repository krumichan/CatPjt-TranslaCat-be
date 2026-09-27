package jp.co.translacat.infrastructure.languagelearning.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.languagelearning.listening.dto.ListeningApiContract;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LanguageLearningListeningClientTest {
    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://ll.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final LanguageLearningListeningClient client = client();

    private LanguageLearningListeningClient client() {
        var properties = new LanguageLearningClientProperties();
        properties.getInternalJwt().setSecretBase64(Base64.getEncoder().encodeToString(new byte[32]));
        return new LanguageLearningListeningClient(builder.build(),
                new LanguageLearningInternalJwtProvider(properties, Clock.systemUTC()),
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void pendingTaskKeepsExternalEnumAndNullEvaluation() {
        // 준비: LL 저장 상태와 외부 READY enum을 구분한 실제 DTO 형식이다.
        server.expect(
                        requestTo("http://ll.test/internal/v1/language-learning/listening/attempts/-7/responses/DICTATION"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Bearer ")))
                .andExpect(content().json("{\"answer\":\"synthetic\"}", true))
                .andRespond(withSuccess("""
                        {"taskResponseId":-8,"taskType":"DICTATION","status":"READY","answerText":null,
                         "audioUploaded":false,"audioDurationMs":null,"audioAvailability":{"available":false,
                         "expired":false,"retentionUntil":null,"deletedAt":null},"rerecordCount":0,
                         "assistanceLevel":"INDEPENDENT","assistanceUsage":[],"evaluationErrorCode":null,"evaluation":null}
                        """, MediaType.APPLICATION_JSON));

        // 실행
        var task = client.post(801L, "/attempts/-7/responses/DICTATION", Map.of("answer", "synthetic"),
                ListeningApiContract.TaskView.class);

        // 검증
        assertEquals(-8L, task.taskResponseId());
        assertEquals("READY", task.status().name());
        assertNull(task.evaluation());
        server.verify();
    }

    @Test
    void businessConflictIsForwardedWithoutAnotherCall() {
        // 준비
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/listening/sessions"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"LISTENING_ACTIVE_SESSION_EXISTS\",\"message\":\"이미 진행 중인 Listening Session이 있습니다.\"}"));

        // 실행
        var failure = assertThrows(LanguageLearningServiceException.class,
                () -> client.post(801L, "/sessions", Map.of(), ListeningApiContract.SessionView.class));

        // 검증: ApiExceptionAdvice가 기존 ListeningErrorDto와 409 응답을 결정한다.
        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatus());
        assertEquals("LISTENING_ACTIVE_SESSION_EXISTS", failure.getErrorCode());
        server.verify();
    }

    @Test
    void audioBytesAndContentTypeArePreserved() {
        // 준비
        byte[] bytes = {82, 73, 70, 70, 1, 2, 3, 4};
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/listening/items/-12/audio"))
                .andRespond(withSuccess(bytes, MediaType.parseMediaType("audio/wav")));

        // 실행
        var audio = client.audio(801L, "/items/-12/audio");

        // 검증
        assertArrayEquals(bytes, audio.bytes());
        assertEquals("audio/wav", audio.contentType());
        assertNull(audio.objectKey());
        server.verify();
    }

    @Test
    void incompleteSuccessIsAProtocolFailure() {
        // 준비
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/listening/daily-sets/-1"))
                .andRespond(withSuccess("{\"dailySetId\":-1}", MediaType.APPLICATION_JSON));

        // 실행
        var failure = assertThrows(LanguageLearningServiceException.class,
                () -> client.get(801L, "/daily-sets/-1", ListeningApiContract.DailySetView.class));

        // 검증
        assertEquals("LL_LISTENING_CONTRACT_ERROR", failure.getErrorCode());
        server.verify();
    }
}
