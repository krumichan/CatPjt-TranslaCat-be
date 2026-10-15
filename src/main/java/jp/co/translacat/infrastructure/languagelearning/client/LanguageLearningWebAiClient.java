package jp.co.translacat.infrastructure.languagelearning.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;

/** Web AI 계획과 결과의 소유권·검증·저장은 LL이 처리한다. */
public class LanguageLearningWebAiClient {
    private static final String ROOT = "/internal/v1/language-learning/web-ai";
    private static final int RESPONSE_LIMIT = 4 * 1024 * 1024;
    private final RestClient http;
    private final LanguageLearningInternalJwtProvider jwt;
    private final ObjectMapper json;

    public LanguageLearningWebAiClient(RestClient http, LanguageLearningInternalJwtProvider jwt, ObjectMapper json) {
        this.http = http;
        this.jwt = jwt;
        this.json = json.copy().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public JsonNode plans(Long userId) {
        return read(userRequest(http.get().uri(ROOT + "/plans"), userId), "plans");
    }

    public JsonNode createPlan(Long userId, JsonNode body) {
        return write(http.post().uri(ROOT + "/plans"), userId, body);
    }

    public JsonNode updatePlan(Long userId, String planId, JsonNode body) {
        return write(http.put().uri(ROOT + "/plans/" + id(planId)), userId, body);
    }

    public void deletePlan(Long userId, String planId) {
        empty(userRequest(http.delete().uri(ROOT + "/plans/" + id(planId)), userId));
    }

    public JsonNode connect(Long userId, String planId) {
        return write(http.post().uri(ROOT + "/plans/" + id(planId) + "/connection"), userId, json.createObjectNode());
    }

    public void revoke(Long userId, String planId) {
        empty(userRequest(http.delete().uri(ROOT + "/plans/" + id(planId) + "/connection"), userId));
    }

    public JsonNode createRun(Long userId, String planId, JsonNode body) {
        return write(http.post().uri(ROOT + "/plans/" + id(planId) + "/runs"), userId, body);
    }

    public JsonNode runs(Long userId, int limit) {
        return read(userRequest(http.get().uri(uri -> uri.path(ROOT + "/runs")
                .queryParam("limit", limit).build()), userId), "runs");
    }

    public JsonNode run(Long userId, String runId) {
        return read(userRequest(http.get().uri(ROOT + "/runs/" + id(runId)), userId), null);
    }

    public JsonNode submit(Long userId, JsonNode body) {
        return write(http.post().uri(ROOT + "/results"), userId, body);
    }

    public JsonNode results(Long userId, int limit) {
        return read(userRequest(http.get().uri(uri -> uri.path(ROOT + "/results")
                .queryParam("limit", limit).build()), userId), "results");
    }

    public JsonNode result(Long userId, String resultId) {
        return read(userRequest(http.get().uri(ROOT + "/results/" + id(resultId)), userId), null);
    }

    public JsonNode retry(Long userId, String resultId) {
        return write(http.post().uri(ROOT + "/results/" + id(resultId) + "/retry"), userId,
                json.createObjectNode());
    }

    public JsonNode externalPrompt(String token, JsonNode body) {
        return external("/external/prompt", token, body);
    }

    public JsonNode externalResult(String token, JsonNode body) {
        return external("/external/results", token, body);
    }

    private JsonNode external(String path, String token, JsonNode body) {
        // 서비스 인증과 계획의 capability를 별도 헤더로 전달해 사용자 JWT와 혼용하지 않는다.
        return read(http.post().uri(ROOT + path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.issueWebAiServiceToken())
                .header("X-Web-Ai-Token", token)
                .body(object(body)), null);
    }

    private JsonNode write(RestClient.RequestBodySpec request, Long userId, JsonNode body) {
        return read(userRequest(request.body(object(body)), userId), null);
    }

    private RestClient.RequestHeadersSpec<?> userRequest(RestClient.RequestHeadersSpec<?> request, Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자 식별자가 필요합니다.");
        }
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.issueUserToken(userId));
    }

    private JsonNode object(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new LanguageLearningServiceException(HttpStatus.BAD_REQUEST, "WEB_AI_REQUEST_INVALID",
                    "Web AI 요청 형식을 확인해 주세요.");
        }
        return body;
    }

    private JsonNode read(RestClient.RequestHeadersSpec<?> request, String arrayField) {
        // 긴 원문 피드백은 보존하되 비정상 응답의 크기와 JSON 형식은 경계에서 제한한다.
        return exchange(request, false, arrayField);
    }

    private void empty(RestClient.RequestHeadersSpec<?> request) {
        exchange(request, true, null);
    }

    private JsonNode exchange(RestClient.RequestHeadersSpec<?> request, boolean empty, String arrayField) {
        try {
            return request.exchange((sent, response) -> {
                int status = response.getStatusCode().value();
                int limit = status >= 400 ? 65_536 : RESPONSE_LIMIT;
                byte[] bytes = response.getBody().readNBytes(limit + 1);
                if (bytes.length > limit) {
                    throw contractFailure();
                }

                // 실패는 재시도하지 않는다. LL의 멱등 키로 명시적 재시도만 같은 작업에 연결한다.
                if (status >= 400) {
                    throw remoteFailure(status, bytes);
                }
                if (status < 200 || status >= 300) {
                    throw contractFailure();
                }
                if (empty) {
                    if (status != 204 && bytes.length > 0) {
                        throw contractFailure();
                    }
                    return null;
                }
                JsonNode value;
                try {
                    value = json.readTree(bytes);
                } catch (IOException invalid) {
                    throw contractFailure();
                }
                if (value == null || !value.isObject()
                        || (arrayField != null && !value.path(arrayField).isArray())) {
                    throw contractFailure();
                }
                return value;
            });
        } catch (RestClientException failure) {
            throw new LanguageLearningServiceException(HttpStatus.BAD_GATEWAY, "LL_WEB_AI_UNAVAILABLE",
                    "Web AI 학습 서비스에 연결하지 못했습니다.", failure);
        }
    }

    private LanguageLearningServiceException remoteFailure(int status, byte[] bytes) {
        try {
            JsonNode error = json.readTree(bytes);
            if (error == null || !error.path("code").isTextual() || !error.path("message").isTextual()) {
                return contractFailure();
            }
            HttpStatus resolved = HttpStatus.resolve(status);
            return new LanguageLearningServiceException(resolved == null ? HttpStatus.BAD_GATEWAY : resolved,
                    error.path("code").asText(), error.path("message").asText());
        } catch (IOException | IllegalArgumentException invalid) {
            return contractFailure();
        }
    }

    private String id(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,100}")) {
            throw new LanguageLearningServiceException(HttpStatus.BAD_REQUEST, "WEB_AI_ID_INVALID",
                    "Web AI 학습 식별자를 확인해 주세요.");
        }
        return value;
    }

    private static LanguageLearningServiceException contractFailure() {
        return new LanguageLearningServiceException(HttpStatus.BAD_GATEWAY, "LL_WEB_AI_CONTRACT_ERROR",
                "Web AI 학습 서비스 응답 형식이 올바르지 않습니다.");
    }
}
