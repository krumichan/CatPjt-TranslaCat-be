package jp.co.translacat.infrastructure.languagelearning.growth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.growth.model.GrowthActivitySnapshot;
import jp.co.translacat.domain.languagelearning.growth.model.GrowthSnapshot;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.util.*;

public class GrowthHttpClient {
    private final RestClient client;
    private final LanguageLearningInternalJwtProvider jwt;
    private final ObjectMapper mapper;

    public GrowthHttpClient(RestClient client, LanguageLearningInternalJwtProvider jwt, ObjectMapper mapper) {
        this.client = client;
        this.jwt = jwt;
        this.mapper = mapper;
    }

    public GrowthSnapshot snapshot(long userId, List<String> keys) {
        // 인증된 사용자에게 필요한 현재 LL mastery 범위만 요청한다.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("masteryKeys", keys);

        // 다른 사용자의 응답과 잘못된 본문은 외부 Profile로 반환하지 않는다.
        try {
            var result = decode(client.post().uri("/internal/v1/language-learning/growth/snapshot")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.issueUserToken(userId))
                            .contentType(MediaType.APPLICATION_JSON).body(payload).retrieve().body(byte[].class),
                    GrowthSnapshot.class);
            if (result.userId() != userId) throw invalid();
            return result;
        } catch (RestClientResponseException failure) {
            throw readFailure(failure);
        } catch (ResourceAccessException failure) {
            throw unavailable();
        }
    }

    public ActivityPage activities(long userId, LearningSource source, LocalDate from,
                                   LocalDate to, long after) {
        try {
            var result =
                    decode(client.get()
                            .uri(builder -> builder.path("/internal/v1/language-learning/growth/activities")
                                    .queryParam("source", source == null ? "" : source.name())
                                    .queryParam("from", from)
                                    .queryParam("to", to)
                                    .queryParam("afterId", after)
                                    .build())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.issueUserToken(userId))
                            .retrieve()
                            .body(byte[].class), ActivityPage.class);
            if (result.userId() != userId
                    || result.activities() == null
                    || result.projectionRevision() == null)
                throw invalid();
            return result;
        } catch (RestClientResponseException failure) {
            throw readFailure(failure);
        } catch (ResourceAccessException failure) {
            throw unavailable();
        }
    }

    private <T> T decode(byte[] bytes, Class<T> type) {
        try {
            if (bytes == null || bytes.length == 0 || bytes.length > 4_194_304) throw invalid();
            return Objects.requireNonNull(mapper.readValue(bytes, type));
        } catch (java.io.IOException | NullPointerException failure) {
            throw invalid();
        }
    }

    private LanguageLearningServiceException readFailure(RestClientResponseException failure) {
        String code = "LL_GROWTH_REQUEST_FAILED";
        try {
            var body = mapper.readTree(failure.getResponseBodyAsByteArray());
            String value = body.path("code").asText();
            if ("LEVEL_TEST_REQUIRED".equals(value)) code = value;
        } catch (Exception ignored) { /* 원문 오류는 로그/외부 응답에 노출하지 않는다. */ }
        return new LanguageLearningServiceException(HttpStatus.SERVICE_UNAVAILABLE, code,
                "언어학습 성장 데이터 조회가 실패했습니다.");
    }

    private static LanguageLearningServiceException invalid() {
        return new LanguageLearningServiceException(HttpStatus.BAD_GATEWAY, "LL_GROWTH_CONTRACT_ERROR",
                "성장 서비스 응답이 계약과 다릅니다.");
    }

    private static LanguageLearningServiceException unavailable() {
        return new LanguageLearningServiceException(HttpStatus.SERVICE_UNAVAILABLE, "LL_GROWTH_UNAVAILABLE",
                "성장 서비스에 연결할 수 없습니다.");
    }

    public record ActivityPage(long userId,
                               List<GrowthActivitySnapshot> activities, Long nextAfterId, String projectionRevision) {
    }
}
