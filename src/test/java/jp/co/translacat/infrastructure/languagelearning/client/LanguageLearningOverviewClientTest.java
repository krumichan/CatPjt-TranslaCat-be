package jp.co.translacat.infrastructure.languagelearning.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import jp.co.translacat.domain.languagelearning.dashboard.dto.response.StreakResponseDto;
import jp.co.translacat.domain.languagelearning.history.dto.response.LearningHistoryDetailResponseDto;
import jp.co.translacat.infrastructure.languagelearning.client.config.LanguageLearningClientProperties;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LanguageLearningOverviewClientTest {
    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://ll.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final LanguageLearningOverviewClient client = client();

    private LanguageLearningOverviewClient client() {
        var properties = new LanguageLearningClientProperties();
        properties.getInternalJwt().setSecretBase64(Base64.getEncoder().encodeToString(new byte[32]));
        return new LanguageLearningOverviewClient(builder.build(),
                new LanguageLearningInternalJwtProvider(properties, Clock.systemUTC()), mapper);
    }

    @Test
    void 누적_근거의_필터와_코칭_메타데이터를_그대로_중계한다() {
        // 준비: 코칭은 점수와 다른 저장 결과이며 nullable·추가 필드도 보존한다.
        var query = new LinkedHashMap<String, Object>();
        query.put("source", "SPEAKING");
        query.put("learningLanguage", "en");
        query.put("from", "2026-09-01");
        query.put("to", "2026-10-03");
        query.put("resultKind", "SESSION_COACHING");
        query.put("policyVersion", "free-session-coaching-v1");
        query.put("cursor", "101:20");
        query.put("limit", "10");
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/overview/evidence"
                        + "?source=SPEAKING&learningLanguage=en&from=2026-09-01&to=2026-10-03"
                        + "&resultKind=SESSION_COACHING&policyVersion=free-session-coaching-v1&cursor=101:20&limit=10"))
                .andRespond(withSuccess("{\"items\":[{\"resultKind\":\"SESSION_COACHING\",\"score\":null,"
                        + "\"evidenceStatus\":\"SOURCE_UNAVAILABLE\"}],\"nextCursor\":null}", MediaType.APPLICATION_JSON));

        // 실행
        var result = client.get(101L, "/evidence", query, JsonNode.class);

        // 검증
        assertEquals("SESSION_COACHING", result.path("items").get(0).path("resultKind").asText());
        assertTrue(result.path("items").get(0).path("score").isNull());
        assertEquals("SOURCE_UNAVAILABLE", result.path("items").get(0).path("evidenceStatus").asText());
        assertTrue(result.path("nextCursor").isNull());
        server.verify();
    }

    @Test
    void 조회_조건과_인증된_사용자를_변경없이_전달한다() {
        // 준비
        var query = new LinkedHashMap<String, Object>();
        query.put("source", "LISTENING");
        query.put("period", " 3d ");
        query.put("status", null);
        server.expect(requestTo(
                        "http://ll.test/internal/v1/language-learning/overview/history?source=LISTENING&period=%203d%20"))
                .andExpect(request -> {
                    var token = request.getHeaders().getFirst("Authorization").substring(7);
                    var payload = mapper.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
                    assertEquals("101", payload.path("sub").asText());
                }).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        // 실행
        var result = client.list(101L, "/history", query, LearningHistoryDetailResponseDto.class);

        // 검증
        assertTrue(result.isEmpty());
        server.verify();
    }

    @Test
    void 이력_복합_ID와_필수_null_필드를_보존한다() {
        // 준비
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/overview/history/WRITING:-1"))
                .andRespond(withSuccess("{\"activityId\":\"WRITING:-1\",\"source\":\"WRITING\",\"detail\":{}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/overview/streak"))
                .andRespond(withSuccess("{\"current\":0,\"longest\":0,\"lastStudyDate\":null}",
                        MediaType.APPLICATION_JSON));

        // 실행
        var detail = client.get(101L, "/history/WRITING:-1", Map.of(), LearningHistoryDetailResponseDto.class);
        var streak = client.get(101L, "/streak", Map.of(), StreakResponseDto.class);

        // 검증
        assertEquals("WRITING:-1", detail.activityId());
        assertNull(streak.lastStudyDate());
        server.verify();
    }

    @Test
    void 업무_오류는_그대로_전달하고_누락된_성공_DTO는_실패한다() {
        // 준비
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/overview/dashboard"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"DASHBOARD_SOURCE_INVALID\",\"message\":\"조회 조건 오류\"}"));
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/overview/streak"))
                .andRespond(withSuccess("{\"current\":0}", MediaType.APPLICATION_JSON));

        // 실행
        var business = assertThrows(LanguageLearningServiceException.class,
                () -> client.get(101L, "/dashboard", Map.of(), StreakResponseDto.class));
        var invalid = assertThrows(LanguageLearningServiceException.class,
                () -> client.get(101L, "/streak", Map.of(), StreakResponseDto.class));

        // 검증
        assertEquals("DASHBOARD_SOURCE_INVALID", business.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, business.getStatus());
        assertEquals("LL_OVERVIEW_CONTRACT_ERROR", invalid.getErrorCode());
        server.verify();
    }
}
