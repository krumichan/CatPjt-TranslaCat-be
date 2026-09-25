package jp.co.translacat.infrastructure.languagelearning.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.languagelearning.keyword.dto.request.KeywordCreateRequestDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.request.KeywordUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.request.SystemKeywordSelectionRequestDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.response.KeywordListResponseDto;
import jp.co.translacat.domain.languagelearning.keyword.dto.response.KeywordResponseDto;
import jp.co.translacat.domain.languagelearning.keyword.model.KeywordCandidateSnapshot;
import jp.co.translacat.infrastructure.languagelearning.client.dto.InternalApiErrorDto;
import jp.co.translacat.infrastructure.languagelearning.client.dto.KeywordCandidatesDto;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * 키워드의 유일한 원본은 LL이다. 자동 재시도, Core 테이블 fallback 및 dual-write는 없다.
 */
public class LanguageLearningKeywordClient {
    private static final String USER = "/internal/v1/language-learning/keywords";
    private static final String ADMIN = "/internal/v1/admin/language-learning/system-keywords";
    private final RestClient client;
    private final LanguageLearningInternalJwtProvider jwt;
    private final ObjectMapper json;

    public LanguageLearningKeywordClient(RestClient client, LanguageLearningInternalJwtProvider jwt,
                                         ObjectMapper json) {
        this.client = client;
        this.jwt = jwt;
        this.json = json.copy().enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public KeywordListResponseDto list(Long userId, boolean started, String locale) {
        return read(() -> client.get().uri(USER)
                .header(HttpHeaders.AUTHORIZATION, bearer(jwt.issueKeywordUserToken(userId, started)))
                .header("X-TranslaCat-Locale", locale == null ? "ko" : locale)
                .retrieve().body(byte[].class), KeywordListResponseDto.class);
    }

    public KeywordResponseDto createCustom(Long userId, boolean started, KeywordCreateRequestDto request) {
        return read(() -> client.post().uri(USER + "/custom")
                .header(HttpHeaders.AUTHORIZATION, bearer(jwt.issueKeywordUserToken(userId, started)))
                .body(request).retrieve().body(byte[].class), KeywordResponseDto.class);
    }

    public KeywordResponseDto updateCustom(Long userId, boolean started, Long keywordId,
                                           KeywordUpdateRequestDto request) {
        requireId(keywordId);
        return read(() -> client.patch().uri(USER + "/custom/" + keywordId)
                .header(HttpHeaders.AUTHORIZATION, bearer(jwt.issueKeywordUserToken(userId, started)))
                .body(request).retrieve().body(byte[].class), KeywordResponseDto.class);
    }

    public void deleteCustom(Long userId, boolean started, Long keywordId) {
        requireId(keywordId);
        Boolean deleted = read(() -> client.delete().uri(USER + "/custom/" + keywordId)
                .header(HttpHeaders.AUTHORIZATION, bearer(jwt.issueKeywordUserToken(userId, started)))
                .retrieve().body(byte[].class), Boolean.class);
        if (!Boolean.TRUE.equals(deleted)) throw invalidResponse();
    }

    public KeywordResponseDto selectSystem(Long userId, boolean started, Long keywordId, boolean selected) {
        requireId(keywordId);
        return read(() -> client.put().uri(USER + "/system/" + keywordId + "/selection")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwt.issueKeywordUserToken(userId, started)))
                        .body(new SystemKeywordSelectionRequestDto(selected)).retrieve().body(byte[].class),
                KeywordResponseDto.class);
    }

    public List<KeywordResponseDto> listSystem(Long adminId) {
        KeywordResponseDto[] rows = read(() -> client.get().uri(ADMIN)
                .header(HttpHeaders.AUTHORIZATION, bearer(jwt.issueKeywordAdminToken(adminId)))
                .retrieve().body(byte[].class), KeywordResponseDto[].class);
        return List.copyOf(Arrays.asList(rows));
    }

    public KeywordResponseDto createSystem(Long adminId, KeywordCreateRequestDto request) {
        return read(() -> client.post().uri(ADMIN)
                .header(HttpHeaders.AUTHORIZATION, bearer(jwt.issueKeywordAdminToken(adminId)))
                .body(request).retrieve().body(byte[].class), KeywordResponseDto.class);
    }

    public KeywordResponseDto updateSystem(Long adminId, Long keywordId, KeywordUpdateRequestDto request) {
        requireId(keywordId);
        return read(() -> client.patch().uri(ADMIN + "/" + keywordId)
                .header(HttpHeaders.AUTHORIZATION, bearer(jwt.issueKeywordAdminToken(adminId)))
                .body(request).retrieve().body(byte[].class), KeywordResponseDto.class);
    }

    public List<KeywordCandidateSnapshot> candidates(Long userId, boolean started, LocalDate date) {
        if (date == null) throw new IllegalArgumentException("학습 날짜가 필요합니다.");
        return read(
                () -> client.get().uri(uri -> uri.path(USER + "/candidates").queryParam("learningDate", date).build())
                        .header(HttpHeaders.AUTHORIZATION, bearer(jwt.issueKeywordUserToken(userId, started)))
                        .retrieve().body(byte[].class), KeywordCandidatesDto.class).candidates();
    }

    private <T> T read(Call call, Class<T> type) {
        try {
            byte[] bytes = call.execute();
            if (bytes == null || bytes.length == 0 || bytes.length > 1_048_576) throw invalidResponse();
            T value = json.readValue(bytes, type);
            validate(value);
            return value;
        } catch (RestClientResponseException failure) {
            InternalApiErrorDto error = readError(failure.getResponseBodyAsByteArray());
            HttpStatus status = HttpStatus.resolve(failure.getStatusCode().value());
            throw new LanguageLearningServiceException(status == null ? HttpStatus.BAD_GATEWAY : status,
                    error == null || error.code() == null ? "LL_SERVICE_ERROR" : error.code(),
                    error == null || error.message() == null ? "키워드 서비스 요청에 실패했습니다." : error.message(), failure);
        } catch (ResourceAccessException failure) {
            throw new LanguageLearningServiceException(HttpStatus.BAD_GATEWAY, "LL_SERVICE_UNAVAILABLE",
                    "키워드 서비스에 연결할 수 없습니다.", failure);
        } catch (IOException | RestClientException | IllegalArgumentException failure) {
            throw new LanguageLearningServiceException(HttpStatus.BAD_GATEWAY, "LL_SERVICE_RESPONSE_ERROR",
                    "키워드 서비스 응답을 처리할 수 없습니다.", failure);
        }
    }

    private static void validate(Object value) {
        if (value == null) throw invalidResponse();
        if (value instanceof KeywordResponseDto row) {
            if (row.id() == null
                    || row.id() <= 0
                    || row.text() == null
                    || row.type() == null
                    || row.source() == null
                    || row.sortOrder() < 0) throw invalidResponse();
        } else if (value instanceof KeywordListResponseDto rows) {
            if (rows.systemKeywords() == null || rows.customKeywords() == null) throw invalidResponse();
            rows.systemKeywords().forEach(LanguageLearningKeywordClient::validate);
            rows.customKeywords().forEach(LanguageLearningKeywordClient::validate);
        } else if (value instanceof KeywordResponseDto[] rows) {
            Arrays.stream(rows).forEach(LanguageLearningKeywordClient::validate);
        } else if (value instanceof KeywordCandidatesDto rows) {
            if (rows.candidates() == null) throw invalidResponse();
            rows.candidates().forEach(LanguageLearningKeywordClient::validate);
        } else if (value instanceof KeywordCandidateSnapshot row) {
            if (row.key() == null
                    || row.text() == null
                    || row.type() == null
                    || row.source() == null
                    || row.availableFrom() == null) throw invalidResponse();
        }
    }

    private InternalApiErrorDto readError(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > 65_536) return null;
        try {
            return json.readValue(bytes, InternalApiErrorDto.class);
        } catch (IOException | IllegalArgumentException ignored) {
            return null;
        }
    }

    private static LanguageLearningServiceException invalidResponse() {
        return new LanguageLearningServiceException(HttpStatus.BAD_GATEWAY, "LL_KEYWORDS_CONTRACT_ERROR",
                "키워드 응답 계약이 유효하지 않습니다.");
    }

    private static void requireId(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("키워드 ID는 양수여야 합니다.");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @FunctionalInterface
    private interface Call {
        byte[] execute();
    }
}
