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
    void missingBaselineIsNotFabricated() {
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/level-test/baseline"))
                .andRespond(withSuccess("{\"baseline\":null}", MediaType.APPLICATION_JSON));
        assertTrue(client().baseline(123L).isEmpty());
        server.verify();
    }

    @Test
    void wrongOwnerBaselineIsRejected() {
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/level-test/baseline"))
                .andRespond(withSuccess(
                        "{\"baseline\":{\"userId\":456,\"sessionId\":1,\"completionId\":\"5f55806e-767d-4c0d-82fd-66e44ee094f2\",\"sessionType\":\"INITIAL\",\"score\":65,\"proficiencyBand\":\"INTERMEDIATE\",\"completedDate\":\"2026-09-25\",\"startedAt\":\"2026-09-25T00:00:00\",\"completedAt\":\"2026-09-25T01:00:00\"}}",
                        MediaType.APPLICATION_JSON));
        assertEquals("LL_LEVEL_OWNER_MISMATCH",
                assertThrows(LanguageLearningServiceException.class, () -> client().baseline(123L)).getErrorCode());
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
    void missingCompletionFieldsAreRejected() {
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/level-test/baseline"))
                .andRespond(withSuccess("{\"baseline\":{\"userId\":123}}", MediaType.APPLICATION_JSON));
        assertEquals("LL_LEVEL_RESPONSE_INVALID",
                assertThrows(LanguageLearningServiceException.class, () -> client().baseline(123L)).getErrorCode());
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
