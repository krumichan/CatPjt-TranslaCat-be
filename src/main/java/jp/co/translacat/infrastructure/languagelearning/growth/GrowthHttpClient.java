package jp.co.translacat.infrastructure.languagelearning.growth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.growth.model.GrowthActivitySnapshot;
import jp.co.translacat.domain.languagelearning.growth.model.GrowthOperation;
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

    public GrowthAcknowledgement deliver(GrowthEnvelope event) {
        var bytes = client.post().uri("/internal/v1/service/language-learning/growth/commands")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.issueGrowthServiceToken())
                .contentType(MediaType.APPLICATION_JSON).body(event).retrieve().body(byte[].class);
        return decode(bytes, GrowthAcknowledgement.class);
    }

    public GrowthSnapshot snapshot(long userId, String source, long minimum, List<GrowthOperation> preview,
                                   List<String> keys) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceInstanceId", source);
        payload.put("minimumSequence", minimum);
        payload.put("previewOperations", preview);
        payload.put("masteryKeys", keys);
        try {
            var result = decode(client.post().uri("/internal/v1/language-learning/growth/snapshot")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.issueUserToken(userId))
                            .contentType(MediaType.APPLICATION_JSON).body(payload).retrieve().body(byte[].class),
                    GrowthSnapshot.class);
            if (result.userId() != userId || !source.equals(result.sourceInstanceId()) || result.sequence() < minimum
                    || result.preview() != !preview.isEmpty()) throw invalid();
            return result;
        } catch (RestClientResponseException failure) {
            throw readFailure(failure);
        } catch (ResourceAccessException failure) {
            throw unavailable();
        }
    }

    public ActivityPage activities(long userId, String sourceId, long minimum, LearningSource source, LocalDate from,
                                   LocalDate to, long after) {
        try {
            var result =
                    decode(client.get()
                            .uri(builder -> builder.path("/internal/v1/language-learning/growth/activities")
                                    .queryParam("sourceInstanceId", sourceId)
                                    .queryParam("minimumSequence", minimum)
                                    .queryParam("source", source == null ? "" : source.name())
                                    .queryParam("from", from)
                                    .queryParam("to", to)
                                    .queryParam("afterId", after)
                                    .build())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.issueUserToken(userId))
                            .retrieve()
                            .body(byte[].class), ActivityPage.class);
            if (result.userId() != userId
                    || !sourceId.equals(result.sourceInstanceId())
                    || result.sequence() < minimum
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
            if (Set.of("GROWTH_SYNC_PENDING", "GROWTH_SOURCE_MISMATCH", "GROWTH_OPERATION_CONFLICT",
                    "LEVEL_TEST_REQUIRED").contains(value)) code = value;
        } catch (Exception ignored) { /* 원문 오류는 로그/외부 응답에 노출하지 않는다. */ }
        return new LanguageLearningServiceException(HttpStatus.SERVICE_UNAVAILABLE, code,
                "GROWTH_SYNC_PENDING".equals(code) ? "성장 결과 반영 중입니다. 잠시 후 다시 확인해 주세요." : "언어학습 성장 데이터 조회가 실패했습니다.");
    }

    private static LanguageLearningServiceException invalid() {
        return new LanguageLearningServiceException(HttpStatus.BAD_GATEWAY, "LL_GROWTH_CONTRACT_ERROR",
                "성장 서비스 응답이 계약과 다릅니다.");
    }

    private static LanguageLearningServiceException unavailable() {
        return new LanguageLearningServiceException(HttpStatus.SERVICE_UNAVAILABLE, "LL_GROWTH_UNAVAILABLE",
                "성장 서비스에 연결할 수 없습니다.");
    }

    public record ActivityPage(long userId, String sourceInstanceId, long sequence,
                               List<GrowthActivitySnapshot> activities, Long nextAfterId, String projectionRevision) {
    }
}
