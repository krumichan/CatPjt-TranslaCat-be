package jp.co.translacat.infrastructure.languagelearning.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.practice.dto.request.PracticeAnswerSubmitRequestDto;
import jp.co.translacat.domain.languagelearning.practice.dto.response.*;
import jp.co.translacat.domain.languagelearning.practice.model.PracticeReportSnapshot;
import jp.co.translacat.domain.languagelearning.practice.port.PracticeGateway;
import jp.co.translacat.infrastructure.languagelearning.client.dto.InternalApiErrorDto;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class LanguageLearningPracticeClient implements PracticeGateway {
    private static final String ROOT = "/internal/v1/language-learning/practice";
    private final RestClient http;
    private final LanguageLearningInternalJwtProvider jwt;
    private final ObjectMapper json;

    public LanguageLearningPracticeClient(RestClient http, LanguageLearningInternalJwtProvider jwt, ObjectMapper json) {
        this.http = http;
        this.jwt = jwt;
        this.json = json.copy().enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Override
    public PracticeSetResponseDto today(Long userId, PracticeDomain domain, String mode) {
        return read(http.get().uri(builder -> builder.path(ROOT + "/today")
                        .queryParam("domain", domain).queryParam("mode", mode).build()), userId,
                type(PracticeSetResponseDto.class));
    }

    @Override
    public PracticeSetResponseDto get(Long userId, Long setId) {
        return read(http.get().uri(ROOT + "/sets/" + id(setId)), userId, type(PracticeSetResponseDto.class));
    }

    @Override
    public PracticeSetResponseDto retry(Long userId, Long setId) {
        return post(userId, "/sets/" + id(setId) + "/retry-generation", Map.of(), PracticeSetResponseDto.class);
    }

    @Override
    public List<PracticeTodayModeStatusResponseDto> statuses(Long userId, PracticeDomain domain) {
        return read(
                http.get().uri(builder -> builder.path(ROOT + "/today/status").queryParam("domain", domain).build()),
                userId,
                json.getTypeFactory().constructCollectionType(List.class, PracticeTodayModeStatusResponseDto.class));
    }

    @Override
    public List<PracticeModeAvailabilityResponseDto> availability(Long userId) {
        return read(http.get().uri(ROOT + "/today/availability"), userId,
                json.getTypeFactory().constructCollectionType(List.class, PracticeModeAvailabilityResponseDto.class));
    }

    @Override
    public PracticeAnswerResultResponseDto answer(Long userId, Long questionId,
                                                  PracticeAnswerSubmitRequestDto request) {
        return post(userId, "/questions/" + id(questionId) + "/answers", request,
                PracticeAnswerResultResponseDto.class);
    }

    @Override
    public VocabularyMasterySummaryResponseDto mastery(Long userId) {
        return read(http.get().uri(ROOT + "/vocabulary/mastery"), userId,
                type(VocabularyMasterySummaryResponseDto.class));
    }

    public PracticeReportSnapshot report(Long userId, LocalDate from, LocalDate to) {
        if ((from == null) != (to == null)) throw new IllegalArgumentException("조회 시작일과 종료일을 함께 지정해 주세요.");
        String query = from == null ? "" : "?from=" + from + "&to=" + to;
        return read(http.get().uri(ROOT + "/report" + query), userId, type(PracticeReportSnapshot.class));
    }

    private <T> T post(Long userId, String path, Object body, Class<T> type) {
        try {
            return read(http.post()
                            .uri(ROOT + path)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(json.writeValueAsBytes(body)),
                    userId, type(type));
        } catch (IOException failure) {
            throw contractFailure();
        }
    }

    private <T> T read(RestClient.RequestHeadersSpec<?> request, Long userId, JavaType type) {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("사용자 식별자가 필요합니다.");
        try {
            // 내부 JWT는 BE가 검증한 사용자를 결합하며 응답 크기와 오류 DTO를 제한한다.
            return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.issueUserToken(userId))
                    .exchange((sent, response) -> {
                        int status = response.getStatusCode().value();
                        int limit = status >= 400 ? 65536 : 4 * 1024 * 1024;
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
            throw new LanguageLearningServiceException(HttpStatus.BAD_GATEWAY, "LL_PRACTICE_UNAVAILABLE",
                    "Reading 서비스에 연결하지 못했습니다. 같은 학습을 다시 조회해 주세요.", failure);
        }
    }

    private JavaType type(Class<?> value) {
        return json.getTypeFactory().constructType(value);
    }

    private static Long id(Long value) {
        if (value == null || value == 0) throw new IllegalArgumentException("학습 식별자가 필요합니다.");
        return value;
    }

    private static LanguageLearningServiceException contractFailure() {
        return new LanguageLearningServiceException(HttpStatus.BAD_GATEWAY, "LL_PRACTICE_CONTRACT_ERROR",
                "Reading 서비스 응답 계약이 올바르지 않습니다.");
    }
}
