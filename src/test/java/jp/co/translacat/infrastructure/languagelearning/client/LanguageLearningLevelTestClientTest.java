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
    @Test
    void selfSelectionRelaysOnlyBandAndKeyAndAcceptsAZeroQuestionResult() {
        // 준비: 실제 점수는 LL 응답에서만 가져오며 테스트 문항은 없다.
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/level-test/self-selection"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.method(
                        org.springframework.http.HttpMethod.POST))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content().json(
                        "{\"band\":\"B2\",\"idempotencyKey\":\"initial-choice\"}", true))
                .andRespond(withSuccess("""
                        {"sessionId":42,"sessionType":"SELF_SELECTED","status":"COMPLETED",
                         "totalQuestions":0,"currentQuestionNumber":0,"currentComplexityBand":2,
                         "baseLevelScore":47.0,"proficiencyBand":"BASIC",
                         "startedAt":"2026-11-25T01:00:00","completedAt":"2026-11-25T01:00:00"}
                        """, MediaType.APPLICATION_JSON));

        // 실행
        var result = client().selfSelect(123L,
                new jp.co.translacat.domain.languagelearning.level.dto.request.LevelSelfSelectRequestDto(
                        "B2", "initial-choice"));

        // 검증: 자기 선택을 일반 실측으로 바꾸거나 임의 점수를 계산하지 않는다.
        assertEquals(jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionType.SELF_SELECTED,
                result.sessionType());
        assertEquals(0, result.totalQuestions());
        assertEquals(47.0, result.baseLevelScore());
        server.verify();
    }

    @Test
    void selfSelectionWriteFailureDoesNotAutomaticallyRetry() {
        // 준비 / 실행 / 검증: 저장 여부가 불명확한 요청을 다른 키로 재실행하지 않는다.
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/level-test/self-selection"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"LL_LEVEL_SERVICE_UNAVAILABLE\",\"message\":\"unavailable\"}"));
        assertThrows(LanguageLearningServiceException.class, () -> client().selfSelect(123L,
                new jp.co.translacat.domain.languagelearning.level.dto.request.LevelSelfSelectRequestDto("B1", "retry")));
        server.verify();
    }

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://ll.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private LanguageLearningLevelTestClient client() {
        var p = new LanguageLearningClientProperties();
        p.getInternalJwt().setSecretBase64(java.util.Base64.getEncoder().encodeToString(new byte[32]));
        return new LanguageLearningLevelTestClient(builder.build(),
                new LanguageLearningInternalJwtProvider(
                        p, Clock.fixed(Instant.parse("2026-09-25T01:00:00Z"), ZoneOffset.UTC)),
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
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("{\"code\":\"AI_SERVER_UNAVAILABLE\",\"message\":\"unavailable\"}"));
        assertThrows(LanguageLearningServiceException.class,
                ()
                        -> client().start(123L,
                                new jp.co.translacat.domain.languagelearning.level.dto.request.LevelTestStartRequestDto(
                                        jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionType
                                                .INITIAL,
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

    @Test
    void historyPreservesLanguagePairsAndAcceptsOlderHistoryWithoutTags() {
        // 준비: 완료 원본의 영어 언어쌍과 이전 nullable 메타데이터 계약을 함께 검증한다.
        String legacy = """
                {"sessionId":1,"sessionType":"INITIAL","overallScore":80,"proficiencyBand":"ADVANCED",
                 "domainScores":null,"completedAt":"2026-10-16T01:00:00"}
                """;
        String tagged = legacy.strip();
        tagged = tagged.substring(0, tagged.length() - 1) + ",\"originLanguage\":\"ko\",\"learningLanguage\":\"en\"}";
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/level-test/history"))
                .andRespond(withSuccess("[" + tagged + "]", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/level-test/history"))
                .andRespond(withSuccess("[" + legacy + "]", MediaType.APPLICATION_JSON));
        var client = client();

        // 실행
        var current = client.history(123L).getFirst();
        var older = client.history(123L).getFirst();

        // 검증
        assertEquals("ko", current.originLanguage());
        assertEquals("en", current.learningLanguage());
        assertNull(older.originLanguage());
        assertNull(older.learningLanguage());
        assertEquals(80, older.overallScore());
        server.verify();
    }
}
