package jp.co.translacat.infrastructure.languagelearning.client;

import io.jsonwebtoken.io.Encoders;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.setting.dto.request.UserSettingUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.setting.dto.response.UserSettingResponseDto;
import jp.co.translacat.infrastructure.languagelearning.client.config.LanguageLearningClientProperties;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import static org.junit.jupiter.api.Assertions.assertNull;
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
        // 준비: 설명 언어와 다음 학습일에 적용할 설명 언어를 구분해 전달한다.
        server.expect(requestTo(
                        "http://ll.test/internal/v1/language-learning/settings"
                ))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Bearer ")))
                .andRespond(withSuccess(userResponseJson(), MediaType.APPLICATION_JSON));

        // 실행
        UserSettingResponseDto response = client.getUserSettings(123L);

        // 검증
        assertEquals("ko", response.originLanguage());
        assertEquals("ja", response.learningLanguage());
        assertEquals("en", response.feedbackLanguage());
        assertEquals("ko", response.pendingFeedbackLanguage());
        assertEquals(List.of(ListeningTaskType.DICTATION), response.defaultListeningTaskTypes());
        assertEquals(1, response.minDailySentenceCount());
        assertEquals(20, response.maxDailySentenceCount());
        server.verify();
    }

    @Test
    void patchSerializesExistingExternalRequestContract() {
        // 준비: 설명 언어가 없는 기존 요청도 기존 필드와 함께 전달된다.
        server.expect(requestTo(
                        "http://ll.test/internal/v1/language-learning/settings"
                ))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(jsonPath("$.dailySentenceCount").value(7))
                .andExpect(jsonPath("$.defaultListeningTaskTypes[0]").value("DICTATION"))
                .andRespond(withSuccess(userResponseJson(), MediaType.APPLICATION_JSON));

        // 실행
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

        // 검증
        server.verify();
    }

    @Test
    void patchForwardsFeedbackLanguageWithoutChangingTheLearningPair() {
        // 준비: 설명 언어만 변경할 때 원문·학습 언어는 지정하지 않는다.
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/settings"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(jsonPath("$.feedbackLanguage").value("en"))
                .andExpect(jsonPath("$.originLanguage").doesNotExist())
                .andExpect(jsonPath("$.learningLanguage").doesNotExist())
                .andRespond(withSuccess(userResponseJson(), MediaType.APPLICATION_JSON));
        var request = new UserSettingUpdateRequestDto(
                null, null, "en", null, null, null, null, null, null, null
        );

        // 실행
        var response = client.updateUserSettings(123L, request);

        // 검증: 예약 적용 여부는 LL 응답을 그대로 보존한다.
        assertEquals("en", response.feedbackLanguage());
        assertEquals("ko", response.pendingFeedbackLanguage());
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({"ko, ja", "ja, ko", "ko, en", "en, ko", "ja, en", "en, ja"})
    void patchPreservesAllSupportedLearningPairsAcrossTheLlBoundary(String origin, String learning) {
        // 준비: UI 언어와 분리된 원문·학습 언어 조합을 LL 요청과 응답에 사용한다.
        var responseBody = userResponseJson()
                .replace("\"originLanguage\":\"ko\"", "\"originLanguage\":\"" + origin + "\"")
                .replace("\"learningLanguage\":\"ja\"", "\"learningLanguage\":\"" + learning + "\"");
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/settings"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Bearer ")))
                .andExpect(jsonPath("$.originLanguage").value(origin))
                .andExpect(jsonPath("$.learningLanguage").value(learning))
                .andExpect(jsonPath("$.feedbackLanguage").value("ko"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));
        var request = new UserSettingUpdateRequestDto(
                origin, learning, "ko", null, null, null, null, null, null, null
        );

        // 실행
        var response = client.updateUserSettings(123L, request);

        // 검증: 영어를 포함한 조합과 LL의 설명 언어를 기존 DTO 그대로 전달한다.
        assertEquals(origin, response.originLanguage());
        assertEquals(learning, response.learningLanguage());
        assertEquals("en", response.feedbackLanguage());
        server.verify();
    }

    @Test
    void legacyJsonRequestKeepsFeedbackLanguageUnspecified() throws Exception {
        // 준비: 기존 FE의 설명 언어 없는 요청 형태를 사용한다.
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        // 실행
        var request = mapper.readValue("""
                {"originLanguage":"ko","learningLanguage":"ja","timezone":"Asia/Tokyo"}
                """, UserSettingUpdateRequestDto.class);

        // 검증: 기본값과 기존 설정 보존은 LL이 판단한다.
        assertNull(request.feedbackLanguage());
        assertEquals("ko", request.originLanguage());
        assertEquals("ja", request.learningLanguage());
    }

    @Test
    void olderLlResponseUsesOriginLanguageForMissingFeedbackFields() {
        // 준비: LL 순차 배포 중 이전 계약의 응답을 받는다.
        var legacyResponse = userResponseJson()
                .replace("\"feedbackLanguage\":\"en\",", "")
                .replace("\"pendingFeedbackLanguage\":\"ko\",", "");
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/settings"))
                .andRespond(withSuccess(legacyResponse, MediaType.APPLICATION_JSON));

        // 실행
        var response = client.getUserSettings(123L);

        // 검증: 새 필드의 누락으로 기존 설정 조회 전체가 실패하지 않는다.
        assertEquals("ko", response.feedbackLanguage());
        assertNull(response.pendingFeedbackLanguage());
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
        // 준비: 현재 외부 Listening 정책 조회가 서비스 인증 경로를 계속 사용한다.
        server.expect(
                        requestTo("http://ll.test/internal/v1/service/language-learning/settings/listening-policy"))
                .andExpect(request -> {
                    String token = request.getHeaders().getFirst("Authorization").substring(7);
                    var claims = io.jsonwebtoken.Jwts.parser()
                            .clock(() -> java.util.Date.from(Instant.parse("2026-09-24T07:00:00Z")))
                            .verifyWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(new byte[32]))
                            .build().parseSignedClaims(token).getPayload();
                    assertEquals("ll-settings-service", claims.get("tokenUse"));
                    org.junit.jupiter.api.Assertions.assertNull(claims.get("roles"));
                })
                .andRespond(withSuccess("""
                        {"enabled":true,"defaultItemCount":8,"minItemCount":2,"maxItemCount":12,"hardItemLimit":10,
                         "referenceAudioMaxSeconds":30,"repeatAudioMaxSeconds":60,"maxAudioFileBytes":10485760,
                         "maxRerecordCount":2,"resumeHours":2,"referenceAudioRetentionDays":7,
                         "userAudioRetentionDays":7,"reportedAudioRetentionDays":30,"automaticRetryLimit":2,
                         "manualRetryLimit":1,"practiceAttemptLimit":1,"profilePolicyVersion":"p2",
                         "modelConfigVersion":"m2","referenceTtsRegenerationEnabled":false}
                        """, MediaType.APPLICATION_JSON));

        // 실행
        var result = client.getListeningPolicy();

        // 검증
        assertEquals(8, result.defaultItemCount());
        assertEquals("p2", result.profilePolicyVersion());
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
                  "feedbackLanguage":"en",
                  "timezone":"Asia/Tokyo",
                  "dailySentenceCount":5,
                  "dailySpeakingGoalMinutes":5,
                  "speakingVoiceId":"marin",
                  "speakingPlaybackSpeed":"NORMAL",
                  "dailyListeningGoalCount":5,
                  "defaultListeningTaskTypes":["DICTATION"],
                  "pendingOriginLanguage":null,
                  "pendingLearningLanguage":null,
                  "pendingFeedbackLanguage":"ko",
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
