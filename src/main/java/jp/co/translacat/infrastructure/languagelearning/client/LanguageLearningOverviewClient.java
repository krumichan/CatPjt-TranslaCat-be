package jp.co.translacat.infrastructure.languagelearning.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.languagelearning.dashboard.port.OverviewGateway;
import jp.co.translacat.infrastructure.languagelearning.client.dto.InternalApiErrorDto;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class LanguageLearningOverviewClient implements OverviewGateway {
    private static final String ROOT = "/internal/v1/language-learning/overview";
    private final RestClient http;
    private final LanguageLearningInternalJwtProvider jwt;
    private final ObjectMapper json;

    public LanguageLearningOverviewClient(RestClient http, LanguageLearningInternalJwtProvider jwt, ObjectMapper json) {
        this.http = http;
        this.jwt = jwt;
        this.json = json.copy().enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Override
    public <T> T get(Long userId, String path, Map<String, ?> query, Class<T> type) {
        return read(userId, path, query, json.getTypeFactory().constructType(type));
    }

    @Override
    public <T> List<T> list(Long userId, String path, Map<String, ?> query, Class<T> type) {
        return read(userId, path, query, json.getTypeFactory().constructCollectionType(List.class, type));
    }

    private <T> T read(Long userId, String path, Map<String, ?> query, JavaType type) {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("사용자 식별자가 필요합니다.");
        if (path == null || !path.startsWith("/") || path.contains("..") || path.contains("?")
                || path.contains("\r") || path.contains("\n"))
            throw new IllegalArgumentException("공통 조회 경로가 올바르지 않습니다.");

        // 기존 내부 사용자 인증과 오류 코드를 그대로 전달하고 성공 응답의 누락 DTO를 거부한다.
        try {
            return http.get()
                    .uri(uri -> {
                        uri.path(ROOT + path);
                        query.forEach((key, value) -> {
                            if (value != null) uri.queryParam(key, value);
                        });
                        return uri.build();
                    })
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.issueUserToken(userId))
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        int limit = status >= 400 ? 65_536 : 4 * 1024 * 1024;
                        byte[] bytes = response.getBody().readNBytes(limit + 1);
                        if (bytes.length == 0 || bytes.length > limit) throw contractFailure();
                        if (status >= 400) {
                            InternalApiErrorDto error;
                            try {
                                error = json.readValue(bytes, InternalApiErrorDto.class);
                            } catch (IOException failure) {
                                throw contractFailure();
                            }
                            if (error == null || error.code() == null || error.message() == null)
                                throw contractFailure();
                            throw new LanguageLearningServiceException(HttpStatus.valueOf(status), error.code(),
                                    error.message());
                        }
                        if (status < 200 || status >= 300) throw contractFailure();
                        try {
                            T value = json.readValue(bytes, type);
                            if (value == null) throw contractFailure();
                            return value;
                        } catch (IOException | IllegalArgumentException failure) {
                            throw contractFailure();
                        }
                    });
        } catch (RestClientException failure) {
            throw new LanguageLearningServiceException(HttpStatus.BAD_GATEWAY, "LL_OVERVIEW_UNAVAILABLE",
                    "학습 조회 서비스에 연결하지 못했습니다.", failure);
        }
    }

    private static LanguageLearningServiceException contractFailure() {
        return new LanguageLearningServiceException(HttpStatus.BAD_GATEWAY, "LL_OVERVIEW_CONTRACT_ERROR",
                "학습 조회 응답 계약이 올바르지 않습니다.");
    }
}
