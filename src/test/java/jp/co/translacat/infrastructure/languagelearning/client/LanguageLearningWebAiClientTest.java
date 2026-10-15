package jp.co.translacat.infrastructure.languagelearning.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class LanguageLearningWebAiClientTest {
    private static final String ROOT = "http://ll.test/internal/v1/language-learning/web-ai";
    private static final byte[] KEY = new byte[32];
    private static final String CAPABILITY = "tcwa_synthetic_plan_capability_123456789";
    private final ObjectMapper json = new ObjectMapper();
    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://ll.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final LanguageLearningWebAiClient client = client();

    private LanguageLearningWebAiClient client() {
        var properties = new LanguageLearningClientProperties();
        properties.getInternalJwt().setSecretBase64(Base64.getEncoder().encodeToString(KEY));
        return new LanguageLearningWebAiClient(builder.build(),
                new LanguageLearningInternalJwtProvider(properties, Clock.systemUTC()), json);
    }

    @Test
    void planListUsesAccountIdentityInInternalJwt() {
        // 준비: 요청 query가 아닌 인증된 소유자를 내부 JWT에 결합한다.
        server.expect(requestTo(ROOT + "/plans"))
                .andExpect(request -> {
                    Claims claims = claims(request.getHeaders().getFirst("Authorization"));
                    assertThat(claims.getSubject()).isEqualTo("123");
                    assertThat(claims.get("tokenUse")).isEqualTo("ll-internal");
                })
                .andRespond(withSuccess("{\"plans\":[]}", MediaType.APPLICATION_JSON));

        // 실행
        var plans = client.plans(123L);

        // 검증
        assertThat(plans.path("plans").isArray()).isTrue();
        server.verify();
    }

    @Test
    void externalPromptSeparatesServiceIdentityFromPlanCapability() throws Exception {
        // 준비
        var body = json.readTree("{\"executionKey\":\"2026-10-15T18:00\"}");
        server.expect(requestTo(ROOT + "/external/prompt"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Web-Ai-Token", CAPABILITY))
                .andExpect(content().json(body.toString(), true))
                .andExpect(request -> {
                    Claims claims = claims(request.getHeaders().getFirst("Authorization"));
                    assertThat(claims.getSubject()).isEqualTo("translacat-be");
                    assertThat(claims.get("tokenUse")).isEqualTo("ll-web-ai-service");
                    assertThat(claims.get("scopes")).isEqualTo(List.of("web-ai:external"));
                    assertThat(claims).doesNotContainKeys("roles", "userId");
                })
                .andRespond(withSuccess("{\"id\":\"run-1\",\"prompt\":\"문제를 생성하세요\"}",
                        MediaType.APPLICATION_JSON));

        // 실행
        var run = client.externalPrompt(CAPABILITY, body);

        // 검증
        assertThat(run.path("id").asText()).isEqualTo("run-1");
        server.verify();
    }

    @Test
    void manualImportPreservesOriginalMarkdownAndDetailedFeedback() throws Exception {
        // 준비: 표·일본어·줄바꿈을 재해석하거나 점수만 추출하지 않는다.
        var body = json.readTree("""
                {"runId":"run-1","submissionId":"answer-1","originalFeedbackMarkdown":"## 해설\\n|원문|교정|\\n|特定な|特定の|",
                 "items":[{"itemId":"q1","externalScore":90,"answerText":"日本語の回答"}]}
                """);
        var response = body.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) response).put("id", "result-1");
        server.expect(requestTo(ROOT + "/results"))
                .andExpect(content().json(body.toString(), true))
                .andRespond(withSuccess(response.toString(), MediaType.APPLICATION_JSON));

        // 실행
        var result = client.submit(123L, body);

        // 검증
        assertThat(result.path("originalFeedbackMarkdown")).isEqualTo(body.path("originalFeedbackMarkdown"));
        assertThat(result.path("items")).isEqualTo(body.path("items"));
        server.verify();
    }

    @Test
    void capabilityRejectionKeepsUnauthorizedAndDoesNotRetry() throws Exception {
        // 준비
        server.expect(requestTo(ROOT + "/external/results"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"WEB_AI_TOKEN_REVOKED\",\"message\":\"연결이 해제되었습니다.\"}"));

        // 실행
        var failure = assertThrows(LanguageLearningServiceException.class,
                () -> client.externalResult(CAPABILITY, json.createObjectNode()));

        // 검증
        assertThat(failure.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(failure.getErrorCode()).isEqualTo("WEB_AI_TOKEN_REVOKED");
        server.verify();
    }

    @Test
    void deleteAcceptsEmptyNoContent() {
        // 준비
        server.expect(requestTo(ROOT + "/plans/plan-1/connection"))
                .andExpect(method(HttpMethod.DELETE)).andRespond(withNoContent());

        // 실행
        client.revoke(123L, "plan-1");

        // 검증
        server.verify();
    }

    @Test
    void malformedSuccessAndRedirectAreContractFailures() {
        // 준비
        server.expect(requestTo(ROOT + "/plans"))
                .andRespond(withSuccess("{\"plans\":{}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(ROOT + "/results/result-1"))
                .andRespond(withStatus(HttpStatus.TEMPORARY_REDIRECT));

        // 실행
        var malformed = assertThrows(LanguageLearningServiceException.class, () -> client.plans(123L));
        var redirected = assertThrows(LanguageLearningServiceException.class, () -> client.result(123L, "result-1"));

        // 검증
        assertThat(malformed.getErrorCode()).isEqualTo("LL_WEB_AI_CONTRACT_ERROR");
        assertThat(redirected.getErrorCode()).isEqualTo("LL_WEB_AI_CONTRACT_ERROR");
        server.verify();
    }

    @Test
    void unsafePathIsRejectedBeforeNetworkCall() {
        // 준비 / 실행
        var failure = assertThrows(LanguageLearningServiceException.class, () -> client.run(123L, "../other"));

        // 검증
        assertThat(failure.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        server.verify();
    }

    private Claims claims(String authorization) {
        return Jwts.parser().verifyWith(Keys.hmacShaKeyFor(KEY)).build()
                .parseSignedClaims(authorization.substring(7)).getPayload();
    }
}
