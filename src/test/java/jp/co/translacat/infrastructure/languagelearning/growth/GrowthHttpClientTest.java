package jp.co.translacat.infrastructure.languagelearning.growth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import jp.co.translacat.infrastructure.languagelearning.client.config.LanguageLearningClientProperties;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GrowthHttpClientTest {
    private MockRestServiceServer server;
    private GrowthHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private static final String URL = "http://localhost:8081/internal/v1/language-learning/growth/snapshot";

    @BeforeEach
    void setup() {
        // 준비: HTTP 교환만 고정하고 실제 직렬화와 사용자 JWT 발급을 실행한다.
        var builder = RestClient.builder().baseUrl("http://localhost:8081");
        server = MockRestServiceServer.bindTo(builder).build();
        var properties = new LanguageLearningClientProperties();
        properties.getInternalJwt().setSecretBase64(Base64.getEncoder().encodeToString(new byte[32]));
        client = new GrowthHttpClient(builder.build(),
                new LanguageLearningInternalJwtProvider(properties, Clock.systemUTC()), mapper);
    }

    @Test
    void currentSnapshotUsesUserTokenAndOnlyRequestedKeys() {
        // 준비: 내부 조회 계약에서 이관 source·sequence·preview를 제거한다.
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"masteryKeys\":[\"topic:travel\"]}", true))
                .andExpect(request -> {
                    String token = Objects.requireNonNull(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                            .substring(7);
                    var claims = mapper.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
                    assertEquals("123", claims.path("sub").asText());
                    assertEquals("ll-internal", claims.path("tokenUse").asText());
                }).andRespond(withSuccess(snapshot(123), MediaType.APPLICATION_JSON));

        // 실행
        var result = client.snapshot(123, List.of("topic:travel"));

        // 검증
        assertEquals(123, result.userId());
        assertNull(result.profile());
        server.verify();
    }

    @Test
    void otherUserResponseIsRejected() {
        // 준비
        server.expect(requestTo(URL)).andRespond(withSuccess(snapshot(456), MediaType.APPLICATION_JSON));

        // 실행
        var failure = assertThrows(LanguageLearningServiceException.class, () -> client.snapshot(123, null));

        // 검증
        assertEquals("LL_GROWTH_CONTRACT_ERROR", failure.getErrorCode());
        server.verify();
    }

    @Test
    void malformedCurrentSnapshotIsRejected() {
        // 준비
        server.expect(requestTo(URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        // 실행
        var failure = assertThrows(LanguageLearningServiceException.class, () -> client.snapshot(123, null));

        // 검증
        assertEquals("LL_GROWTH_CONTRACT_ERROR", failure.getErrorCode());
        server.verify();
    }

    @Test
    void unavailableCurrentReadDoesNotUseLocalProfileFallback() {
        // 준비
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .body("{\"code\":\"LEVEL_TEST_REQUIRED\"}").contentType(MediaType.APPLICATION_JSON));

        // 실행
        var failure = assertThrows(LanguageLearningServiceException.class, () -> client.snapshot(123, null));

        // 검증
        assertEquals("LEVEL_TEST_REQUIRED", failure.getErrorCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.getStatus());
        server.verify();
    }

    @Test
    void activityQueryKeepsCurrentCursorAndRevisionWithoutMigrationParameters() {
        // 준비
        var from = LocalDate.parse("2026-09-01");
        var to = LocalDate.parse("2026-09-27");
        server.expect(requestTo(
                        "http://localhost:8081/internal/v1/language-learning/growth/activities?source=WRITING&from=2026-09-01&to=2026-09-27&afterId=12"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"userId\":123,\"activities\":[],\"nextAfterId\":null,\"projectionRevision\":\"7\"}",
                        MediaType.APPLICATION_JSON));

        // 실행
        var page = client.activities(123, LearningSource.WRITING, from, to, 12);

        // 검증
        assertEquals("7", page.projectionRevision());
        assertTrue(page.activities().isEmpty());
        server.verify();
    }

    @Test
    void activityPageWithoutRevisionIsRejected() {
        // 준비
        server.expect(anything())
                .andRespond(withSuccess("{\"userId\":123,\"activities\":[]}", MediaType.APPLICATION_JSON));

        // 실행
        var failure = assertThrows(LanguageLearningServiceException.class,
                () -> client.activities(123, null, LocalDate.MIN, LocalDate.MAX, 0));

        // 검증
        assertEquals("LL_GROWTH_CONTRACT_ERROR", failure.getErrorCode());
        server.verify();
    }

    private String snapshot(long userId) {
        return "{\"userId\":" + userId + ",\"profile\":null,\"masteries\":[],\"signals\":{}}";
    }
}
