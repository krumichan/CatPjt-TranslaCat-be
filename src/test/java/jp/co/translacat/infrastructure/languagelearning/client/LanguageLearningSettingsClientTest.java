package jp.co.translacat.infrastructure.languagelearning.client;

import io.jsonwebtoken.io.Encoders;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.setting.dto.request.UserSettingUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.setting.dto.response.UserSettingResponseDto;
import jp.co.translacat.infrastructure.languagelearning.client.config.LanguageLearningClientProperties;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LanguageLearningSettingsClientTest {

    private MockRestServiceServer server;
    private LanguageLearningSettingsClient client;

    @BeforeEach
    void setUp() {
        byte[] key = new byte[32];
        LanguageLearningClientProperties properties =
                new LanguageLearningClientProperties();
        properties.setUrl("http://ll.test");
        properties.getInternalJwt().setSecretBase64(
                Encoders.BASE64.encode(key)
        );

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.baseUrl("http://ll.test").build();
        LanguageLearningInternalJwtProvider jwtProvider =
                new LanguageLearningInternalJwtProvider(
                        properties,
                        Clock.fixed(
                                Instant.parse("2026-09-24T07:00:00Z"),
                                ZoneOffset.UTC
                        )
                );
        client = new LanguageLearningSettingsClient(
                restClient,
                jwtProvider,
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .findAndRegisterModules()
        );
    }

    @Test
    void getUserSettingsUsesInternalBearerTokenAndMapsBody() {
        server.expect(requestTo(
                        "http://ll.test/internal/v1/language-learning/settings"
                ))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Bearer ")))
                .andRespond(withSuccess(userResponseJson(), MediaType.APPLICATION_JSON));

        UserSettingResponseDto response = client.getUserSettings(123L);

        assertEquals("ko", response.originLanguage());
        assertEquals("ja", response.learningLanguage());
        assertEquals(List.of(ListeningTaskType.DICTATION), response.defaultListeningTaskTypes());
        assertEquals(1, response.minDailySentenceCount());
        assertEquals(20, response.maxDailySentenceCount());
        server.verify();
    }

    @Test
    void patchSerializesExistingExternalRequestContract() {
        server.expect(requestTo(
                        "http://ll.test/internal/v1/language-learning/settings"
                ))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(jsonPath("$.dailySentenceCount").value(7))
                .andExpect(jsonPath("$.defaultListeningTaskTypes[0]").value("DICTATION"))
                .andRespond(withSuccess(userResponseJson(), MediaType.APPLICATION_JSON));

        client.updateUserSettings(
                123L,
                new UserSettingUpdateRequestDto(
                        null,
                        null,
                        null,
                        7,
                        null,
                        null,
                        null,
                        null,
                        List.of(ListeningTaskType.DICTATION)
                )
        );

        server.verify();
    }

    @Test
    void ktorErrorContractIsPreserved() {
        server.expect(requestTo(
                        "http://ll.test/internal/v1/language-learning/settings"
                ))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code":"SETTING_NOT_CONFIGURED","message":"설정값을 확인해 주세요."}
                                """));

        LanguageLearningServiceException error = assertThrows(
                LanguageLearningServiceException.class,
                () -> client.getUserSettings(123L)
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("SETTING_NOT_CONFIGURED", error.getErrorCode());
        assertEquals("설정값을 확인해 주세요.", error.getMessage());
        server.verify();
    }

    @Test
    void missingRequiredNumericFieldIsNotSilentlyDefaulted() {
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/settings"))
                .andRespond(withSuccess(userResponseJson().replace("\"dailySentenceCount\":5,", ""),
                        MediaType.APPLICATION_JSON));
        assertThrows(LanguageLearningServiceException.class, () -> client.getUserSettings(123L));
        server.verify();
    }

    @Test
    void serviceReadUsesItsOwnPathAndTokenPurpose() {
        server.expect(
                        requestTo("http://ll.test/internal/v1/service/language-learning/settings/users/123/learning-date"))
                .andExpect(request -> {
                    String token = request.getHeaders().getFirst("Authorization").substring(7);
                    var claims = io.jsonwebtoken.Jwts.parser()
                            .clock(() -> java.util.Date.from(Instant.parse("2026-09-24T07:00:00Z")))
                            .verifyWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(new byte[32]))
                            .build().parseSignedClaims(token).getPayload();
                    assertEquals("ll-settings-service", claims.get("tokenUse"));
                    org.junit.jupiter.api.Assertions.assertNull(claims.get("roles"));
                })
                .andRespond(withSuccess("{\"date\":\"2026-09-24\"}", MediaType.APPLICATION_JSON));
        assertEquals(java.time.LocalDate.of(2026, 9, 24), client.resolveLearningDate(123L).date());
        server.verify();
    }

    @Test
    void serverFailureIsNotAutomaticallyRetried() {
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/settings"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"SETTINGS_POLICY_UNAVAILABLE\",\"message\":\"not ready\"}"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(LanguageLearningServiceException.class, () -> client.getUserSettings(123L)).getStatus());
        server.verify();
    }

    private String userResponseJson() {
        return """
                {
                  "originLanguage":"ko",
                  "learningLanguage":"ja",
                  "timezone":"Asia/Tokyo",
                  "dailySentenceCount":5,
                  "dailySpeakingGoalMinutes":5,
                  "speakingVoiceId":"marin",
                  "speakingPlaybackSpeed":"NORMAL",
                  "dailyListeningGoalCount":5,
                  "defaultListeningTaskTypes":["DICTATION"],
                  "pendingOriginLanguage":null,
                  "pendingLearningLanguage":null,
                  "pendingTimezone":null,
                  "pendingDailySentenceCount":null,
                  "pendingDailySpeakingGoalMinutes":null,
                  "pendingDailyListeningGoalCount":null,
                  "pendingEffectiveDate":null,
                  "minDailySentenceCount":1,
                  "maxDailySentenceCount":20,
                  "minDailySpeakingGoalMinutes":3,
                  "maxDailySpeakingGoalMinutes":20,
                  "minDailyListeningGoalCount":1,
                  "maxDailyListeningGoalCount":20,
                  "configured":true
                }
                """;
    }
}
