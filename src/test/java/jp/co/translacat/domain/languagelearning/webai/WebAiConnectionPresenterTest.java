package jp.co.translacat.domain.languagelearning.webai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebAiConnectionPresenterTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void configuredOriginMakesBootstrapUsableWithoutRequestHost() throws Exception {
        // 준비
        var connection = json.readTree("{\"planId\":\"plan-1\",\"token\":\"tcwa_synthetic_capability_1234567890\"}");

        // 실행
        var presented = new WebAiConnectionPresenter("https://api.example.test/").present(connection);

        // 검증
        assertThat(presented.path("promptUrl").asText()).isEqualTo(
                "https://api.example.test/api/v1/language-learning/web-ai/external/prompt");
        assertThat(presented.path("bootstrapPrompt").asText()).contains(
                "https://api.example.test/api/v1/language-learning/web-ai/external/prompt",
                "https://api.example.test/api/v1/language-learning/web-ai/external/results",
                "tcwa_synthetic_capability_1234567890");
        assertThat(connection.has("bootstrapPrompt")).isFalse();
    }

    @Test
    void absentOriginReturnsPathsForConfiguredFrontendApiResolution() throws Exception {
        // 준비
        var connection = json.readTree("{\"planId\":\"plan-1\",\"token\":\"tcwa_synthetic_capability_1234567890\"}");

        // 실행
        var presented = new WebAiConnectionPresenter("").present(connection);

        // 검증
        assertThat(presented.path("promptUrl").asText()).startsWith("/api/v1/");
    }

    @Test
    void bootstrapIncludesConfiguredScheduleAndHonestPlatformConfirmation() throws Exception {
        // 준비
        var connection = json.readTree("""
                {"planId":"plan-1","token":"tcwa_synthetic_capability_1234567890",
                 "plan":{"name":"주중 일본어","recurrence":"WEEKLY","time":"18:00","timezone":"Asia/Tokyo",
                         "weekdays":[1,3,5],"monthDays":[],"includeLastDay":false,"focus":"private-learning-focus"}}
                """);

        // 실행
        var presented = new WebAiConnectionPresenter("https://api.example.test").present(connection);

        // 검증: 실제 일정만 안내하고 플랫폼이 보고하지 않은 태스크 생성 성공은 주장하지 않는다.
        assertThat(presented.path("bootstrapPrompt").asText()).contains(
                "\"recurrence\":\"WEEKLY\"", "\"time\":\"18:00\"", "\"timezone\":\"Asia/Tokyo\"",
                "\"weekdays\":[1,3,5]", "0=Sunday", "only after the platform reports success", "scheduling is unavailable")
                .doesNotContain("private-learning-focus");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://api.example.test", "https://user:secret@api.example.test", "https://api.example.test/path",
            "https://api.example.test?token=bad", "javascript:alert(1)", "//api.example.test"})
    void unsafeConfiguredOriginsAreRejected(String origin) {
        // 준비 / 실행 / 검증
        assertThrows(IllegalStateException.class, () -> new WebAiConnectionPresenter(origin));
    }
}
