package jp.co.translacat.infrastructure.languagelearning.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.infrastructure.languagelearning.client.config.LanguageLearningClientProperties;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LanguageLearningLevelTestClientTest {
    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://ll.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private LanguageLearningLevelTestClient client() {
        var p = new LanguageLearningClientProperties();
        p.getInternalJwt().setSecretBase64(java.util.Base64.getEncoder().encodeToString(new byte[32]));
        return new LanguageLearningLevelTestClient(builder.build(), new LanguageLearningInternalJwtProvider(p,
                Clock.fixed(Instant.parse("2026-09-25T01:00:00Z"), ZoneOffset.UTC)),
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void publicStatusUsesCurrentRemoteResponseWithoutRecomputingBand() {
        // 준비: 외부 상태 조회는 현재 LL이 확정한 점수·band를 그대로 읽는다.
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/level-test/status"))
                .andRespond(withSuccess("""
                        {"profileState":"ACTIVE","initialLevelTestCompleted":true,"recheckRecommended":false,
                         "activeSessionId":null,"currentQuestionNumber":null,"baseLevelScore":65.0,
                         "proficiencyBand":"INTERMEDIATE"}
                        """, MediaType.APPLICATION_JSON));

        // 실행
        var value = client().status(123L);

        // 검증
        assertTrue(value.initialLevelTestCompleted());
        assertEquals(65.0, value.baseLevelScore());
        assertEquals("INTERMEDIATE", value.proficiencyBand());
        server.verify();
    }

    @Test
    void failedWriteIsNotAutomaticallyRepeated() {
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/level-test/sessions"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"AI_SERVER_UNAVAILABLE\",\"message\":\"unavailable\"}"));
        assertThrows(LanguageLearningServiceException.class, () -> client().start(123L,
                new jp.co.translacat.domain.languagelearning.level.dto.request.LevelTestStartRequestDto(
                        jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionType.INITIAL,
                        "idempotency")));
        server.verify();
    }

    @Test
    void missingPublicStatusFieldsAreRejected() {
        // 준비
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/level-test/status"))
                .andRespond(withSuccess("{\"initialLevelTestCompleted\":true}", MediaType.APPLICATION_JSON));

        // 실행 및 검증
        assertEquals("LL_LEVEL_RESPONSE_INVALID",
                assertThrows(LanguageLearningServiceException.class, () -> client().status(123L)).getErrorCode());
        server.verify();
    }

    @Test
    void audioResponseUsesRawBytesAndPreservesMime() {
        byte[] audio = "RIFF0000WAVEdata".getBytes();
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/level-test/items/1/reference-audio"))
                .andRespond(withSuccess(audio, MediaType.parseMediaType("audio/wav")));
        var result = client().audio(123L, 1L, "reference-audio");
        assertArrayEquals(audio, result.bytes());
        assertEquals("audio/wav", result.contentType());
        server.verify();
    }
}
