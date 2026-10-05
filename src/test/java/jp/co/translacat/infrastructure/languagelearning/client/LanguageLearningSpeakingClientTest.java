package jp.co.translacat.infrastructure.languagelearning.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.languagelearning.speaking.session.dto.response.SpeakingSessionDetailResponseDto;
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

class LanguageLearningSpeakingClientTest {
    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://ll.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final LanguageLearningSpeakingClient client = client();

    private LanguageLearningSpeakingClient client() {
        var properties = new LanguageLearningClientProperties();
        properties.getInternalJwt().setSecretBase64(Base64.getEncoder().encodeToString(new byte[32]));
        return new LanguageLearningSpeakingClient(builder.build(),
                new LanguageLearningInternalJwtProvider(properties, Clock.systemUTC()), mapper);
    }

    @Test
    void activeMayBeNullWhileOrdinaryResponsesMustBePresent() {
        // 준비
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/speaking/sessions/active"))
                .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/speaking/sessions/-1"))
                .andRespond(withSuccess("null", MediaType.APPLICATION_JSON));

        // 실행
        var absent = client.optional(101L, "/sessions/active", Reply.class);
        var failed = assertThrows(LanguageLearningServiceException.class,
                () -> client.get(101L, "/sessions/-1", Reply.class));

        // 검증
        assertNull(absent);
        assertEquals("LL_SPEAKING_CONTRACT_ERROR", failed.getErrorCode());
        server.verify();
    }

    @Test
    void preservesOriginalSpeakingFailureWithoutBusinessFallback() {
        // 준비
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/speaking/sessions/-1/complete"))
                .andExpect(method(HttpMethod.POST)).andExpect(content().json("{\"skipEvaluation\":true}", true))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"SPEAKING_EVALUATION_SKIP_NOT_ALLOWED\",\"message\":\"평가 생략 불가\"}"));

        // 실행
        var failed = assertThrows(LanguageLearningServiceException.class,
                () -> client.post(101L, "/sessions/-1/complete", Map.of("skipEvaluation", true), Reply.class));

        // 검증
        assertEquals(HttpStatus.BAD_REQUEST, failed.getStatus());
        assertEquals("SPEAKING_EVALUATION_SKIP_NOT_ALLOWED", failed.getErrorCode());
        server.verify();
    }

    @Test
    void adminPatchBindsTheAuthenticatedActorAndAdminRole() {
        // 준비
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/speaking/admin/topics/-2"))
                .andExpect(method(HttpMethod.PATCH)).andExpect(request -> {
                    String token = request.getHeaders().getFirst("Authorization").substring(7);
                    var payload = mapper.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
                    assertEquals("303", payload.path("sub").asText());
                    assertEquals("ADMIN", payload.path("roles").get(0).asText());
                }).andRespond(withSuccess("{\"id\":-2,\"status\":\"ACTIVE\"}", MediaType.APPLICATION_JSON));

        // 실행
        var result = client.adminPatch(303L, "/admin/topics/-2", Map.of("title", "Synthetic title"), Reply.class);

        // 검증
        assertEquals(-2L, result.id());
        server.verify();
    }

    @Test
    void incompleteJsonAndNonAudioBytesAreContractFailures() {
        // 준비
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/speaking/sessions/-1"))
                .andRespond(withSuccess("{\"id\":-1}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/speaking/sessions/-1/audio/opening"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        // 실행
        var jsonFailure = assertThrows(LanguageLearningServiceException.class,
                () -> client.get(101L, "/sessions/-1", Reply.class));
        var audioFailure = assertThrows(LanguageLearningServiceException.class,
                () -> client.audio(101L, "/sessions/-1/audio/opening"));

        // 검증
        assertEquals("LL_SPEAKING_CONTRACT_ERROR", jsonFailure.getErrorCode());
        assertEquals("LL_SPEAKING_CONTRACT_ERROR", audioFailure.getErrorCode());
        server.verify();
    }

    @Test
    void relaysSourceEvidenceLimitationsWithoutRestoringHiddenSummaryOrScores() {
        // 준비: LL이 원본 변경 때문에 요약과 코칭 인용을 숨긴 응답을 그대로 중계한다.
        String response = """
                {
                  "session": {
                    "id": -1, "learningDate": "2026-10-03", "topicId": null,
                    "topicTitle": "Synthetic topic", "topicCategory": "FREE_TALK", "topicVersion": null,
                    "customTopic": null, "goal": null, "persona": null,
                    "originLanguage": "ko", "learningLanguage": "en", "status": "COMPLETED",
                    "evaluationStatus": "NOT_REQUESTED", "resultKind": "SESSION_COACHING",
                    "resultPolicyVersion": "free-session-coaching-v1", "resultStatus": "SUCCEEDED",
                    "practiceMode": "FREE", "conversationStartMode": "AI_FIRST", "resolvedStartMode": "AI_FIRST",
                    "correctionMode": "CONVERSATION", "targetMinutes": 5, "maxTurns": 10,
                    "completedTurns": 2, "totalDurationSeconds": 100, "voiceId": "marin", "playbackSpeed": "NORMAL",
                    "openingAssistantText": "Synthetic opening", "openingPromptGuide": null,
                    "openingAssistantAudioUrl": null, "sessionSummary": null,
                    "summaryEvidenceAvailability": "UNAVAILABLE", "summaryEvidenceLimitations": ["SOURCE_CHANGED"],
                    "startedAt": "2026-10-03T00:00:00", "completedAt": "2026-10-03T00:02:00",
                    "lastActivityAt": "2026-10-03T00:02:00"
                  },
                  "dailyUsage": null, "turns": [], "readAloudProblemEvaluations": [], "evaluationEligibility": null,
                  "coachingResult": {
                    "id": -1, "resultKind": "SESSION_COACHING", "resultPolicyVersion": "free-session-coaching-v1",
                    "schemaVersion": "speaking-session-coaching-schema-v1", "sourceSnapshotHash": "synthetic",
                    "contentStatus": "GROUNDED", "limitationReasons": [], "items": [],
                    "promptVersion": "synthetic", "createdAt": "2026-10-03T00:02:00",
                    "evidenceAvailability": "UNAVAILABLE", "evidenceLimitations": ["SOURCE_CHANGED"]
                  },
                  "resumable": false
                }
                """;
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/speaking/sessions/-1"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        // 실행
        var result = client.get(101L, "/sessions/-1", SpeakingSessionDetailResponseDto.class);
        var publicJson = mapper.valueToTree(result);

        // 검증: BE DTO 재직렬화 후에도 현재 원본의 제한과 null이 유지된다.
        assertEquals("UNAVAILABLE", result.coachingResult().evidenceAvailability());
        assertEquals(java.util.List.of("SOURCE_CHANGED"), result.coachingResult().evidenceLimitations());
        assertEquals("UNAVAILABLE", result.session().summaryEvidenceAvailability());
        assertEquals(java.util.List.of("SOURCE_CHANGED"), result.session().summaryEvidenceLimitations());
        assertTrue(publicJson.path("session").path("sessionSummary").isNull());
        assertEquals("UNAVAILABLE", publicJson.path("coachingResult").path("evidenceAvailability").asText());
        assertFalse(publicJson.path("coachingResult").has("overallScore"));
        server.verify();
    }

    private record Reply(long id, String status) {
    }
}
