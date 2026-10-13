package jp.co.translacat.infrastructure.languagelearning.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jp.co.translacat.infrastructure.languagelearning.client.config.LanguageLearningClientProperties;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class WritingPreparationClientTest {
    private static final String ROOT = "http://ll.test/internal/v1/language-learning/writing/curated";
    private final ObjectMapper json = new ObjectMapper();
    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://ll.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final LanguageLearningWritingClient client = client();

    private LanguageLearningWritingClient client() {
        var properties = new LanguageLearningClientProperties();
        properties.getInternalJwt().setSecretBase64(Base64.getEncoder().encodeToString(new byte[32]));
        return new LanguageLearningWritingClient(builder.build(),
                new LanguageLearningInternalJwtProvider(properties, Clock.systemUTC()), json);
    }

    @Test
    void postsOriginalPreparationAndRepairNoteWithAuthenticatedOwnerOnce() throws Exception {
        // 준비: 사용자가 입력한 이유는 수정하거나 검수 결과로 바꾸지 않는다.
        var request = json.readTree("""
                {"requestKey":"prep-1","writingType":"TRANSLATION","targetBand":2,
                 "topicKey":"clinic-appointment","repairOfRequestKey":"prep-original",
                 "repairNotes":"장면이 너무 반복됩니다. 새 장면을 원합니다."}
                """);
        var response = WritingPreparationContractTest.success().put("repairOfRequestKey", "prep-original")
                .put("repairNotes", request.path("repairNotes").textValue())
                .put("canRepair", false).put("repairBlockedReason", "REPAIR_LIMIT");
        server.expect(requestTo(ROOT + "/preparations")).andExpect(method(HttpMethod.POST))
                .andExpect(sent -> {
                    String token = sent.getHeaders().getFirst(HttpHeaders.AUTHORIZATION).substring(7);
                    var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(new byte[32])).build()
                            .parseSignedClaims(token).getPayload();
                    assertEquals("123", claims.getSubject());
                    assertEquals("ll-internal", claims.get("tokenUse"));
                })
                .andExpect(content().json(request.toString(), true))
                .andRespond(withSuccess(response.toString(), MediaType.APPLICATION_JSON));

        // 실행
        var actual = client.writingPreparationStart(123L, request);

        // 검증: POST 1회 외 재시도·재생성은 없다.
        assertEquals(response, actual);
        server.verify();
    }

    @Test
    void getOnlyReadsSavedPreparationAndRejectsDifferentRequestKey() {
        // 준비
        var response = WritingPreparationContractTest.success();
        server.expect(requestTo(ROOT + "/preparations/prep-1")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(response.toString(), MediaType.APPLICATION_JSON));
        server.expect(requestTo(ROOT + "/preparations/prep-other")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(response.toString(), MediaType.APPLICATION_JSON));

        // 실행·검증
        assertEquals(response, client.writingPreparationGet(123L, "prep-1"));
        assertThrows(LanguageLearningServiceException.class, () -> client.writingPreparationGet(123L, "prep-other"));
        server.verify();
    }

    @Test
    void rejectsPathInjectionBeforeAnyRequest() {
        // 준비·실행·검증: 키는 URL 경로를 조립하기 전에 제한한다.
        for (String key : new String[] {"../other", "prep?owner=9", "", "a".repeat(81)}) {
            assertThrows(IllegalArgumentException.class, () -> client.writingPreparationGet(123L, key));
        }
        server.verify();
    }

    @Test
    void preservesOriginalNumericTypeAndProviderFailureWithoutAutomaticRetry() throws Exception {
        // 준비: 소수 입력을 반올림하지 않고 LL의 거절을 보존한다.
        var request = json.readTree("""
                {"requestKey":"prep-1","writingType":"FREE","targetBand":2.5,"topicKey":"school-life"}
                """);
        server.expect(requestTo(ROOT + "/preparations")).andExpect(content().json(request.toString(), true))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"WRITING_REQUEST_INVALID\",\"message\":\"invalid\"}"));
        server.expect(requestTo(ROOT + "/preparations/prep-1"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"MODEL_UNAVAILABLE\",\"message\":\"unavailable\"}"));

        // 실행·검증: 오류를 성공 후보나 다른 모델 호출로 바꾸지 않는다.
        assertEquals("WRITING_REQUEST_INVALID", assertThrows(LanguageLearningServiceException.class,
                () -> client.writingPreparationStart(123L, request)).getErrorCode());
        assertEquals("MODEL_UNAVAILABLE", assertThrows(LanguageLearningServiceException.class,
                () -> client.writingPreparationGet(123L, "prep-1")).getErrorCode());
        server.verify();
    }

    @Test
    void replacementForwardsExactRevisionAndNeverFallsBackToGeneration() throws Exception {
        // 준비: 이미 답한 문항 또는 오래된 revision은 LL의 소유자 트랜잭션에서 거절된다.
        var request = json.readTree("{\"requestKey\":\"repair-1\",\"contentRevision\":\"old-revision\",\"planRevision\":2}");
        server.expect(requestTo(ROOT + "/plans/items/-99/replacement")).andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, org.hamcrest.Matchers.startsWith("Bearer ")))
                .andExpect(content().json(request.toString(), true))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"WRITING_PREPARATION_CONFLICT\",\"message\":\"conflict\"}"));

        // 실행·검증
        var failure = assertThrows(LanguageLearningServiceException.class,
                () -> client.writingPlanReplacement(123L, -99L, request));
        assertEquals("WRITING_PREPARATION_CONFLICT", failure.getErrorCode());
        server.verify();
    }

    @Test
    void replacementUsesExistingPlanResponseContract() throws Exception {
        // 준비: 모델 또는 잘못된 서버가 정상 plan 대신 점수 응답을 반환해도 성공 처리하지 않는다.
        var request = json.readTree("{\"requestKey\":\"repair-1\",\"contentRevision\":\"old-revision\",\"planRevision\":2}");
        server.expect(requestTo(ROOT + "/plans/items/-99/replacement"))
                .andRespond(withSuccess("{\"score\":100}", MediaType.APPLICATION_JSON));

        // 실행·검증
        assertEquals("LL_WRITING_CONTRACT_ERROR", assertThrows(LanguageLearningServiceException.class,
                () -> client.writingPlanReplacement(123L, -99L, request)).getErrorCode());
        server.verify();
    }
}
