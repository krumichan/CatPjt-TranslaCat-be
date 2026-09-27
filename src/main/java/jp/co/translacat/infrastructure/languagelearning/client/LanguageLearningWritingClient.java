package jp.co.translacat.infrastructure.languagelearning.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingType;
import jp.co.translacat.domain.languagelearning.daily.dto.request.AnswerSubmitRequestDto;
import jp.co.translacat.domain.languagelearning.daily.dto.response.AnswerResultResponseDto;
import jp.co.translacat.domain.languagelearning.daily.dto.response.DailyWritingSetResponseDto;
import jp.co.translacat.domain.languagelearning.daily.model.WritingReportSnapshot;
import jp.co.translacat.infrastructure.languagelearning.client.dto.InternalApiErrorDto;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

/**
 * Writing의 저장·평가·재생성을 LL에 한 번만 전달한다. Core DB fallback은 하지 않는다.
 */
public class LanguageLearningWritingClient {
    private static final String ROOT = "/internal/v1/language-learning/writing/daily";
    private static final int MAX_BODY = 4 * 1024 * 1024;
    private final RestClient http;
    private final LanguageLearningInternalJwtProvider jwt;
    private final ObjectMapper json;

    public LanguageLearningWritingClient(RestClient http, LanguageLearningInternalJwtProvider jwt, ObjectMapper json) {
        this.http = http;
        this.jwt = jwt;
        this.json = json.copy()
                .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public DailyWritingSetResponseDto create(Long userId, DailyWritingType writingType) {
        if (writingType == null) throw new IllegalArgumentException("Writing 유형이 필요합니다.");
        return post(userId, "/sets", Map.of("writingType", writingType.name()), DailyWritingSetResponseDto.class);
    }

    public WritingReportSnapshot report(Long userId, LocalDate from, LocalDate to) {
        if ((from == null) != (to == null)) throw new IllegalArgumentException("조회 시작일과 종료일을 함께 지정해 주세요.");
        String query = from == null ? "" : "?from=" + from + "&to=" + to;
        return get(userId, "/report" + query, WritingReportSnapshot.class);
    }

    public DailyWritingSetResponseDto get(Long userId, Long setId) {
        return get(userId, "/sets/" + learningId(setId), DailyWritingSetResponseDto.class);
    }

    public DailyWritingSetResponseDto history(Long userId, LocalDate date, DailyWritingType type) {
        if (date == null || type == null) throw new IllegalArgumentException("조회 날짜와 유형이 필요합니다.");
        return get(userId, "/history/" + date + "?writingType=" + type.name(), DailyWritingSetResponseDto.class);
    }

    public DailyWritingSetResponseDto retry(Long userId, Long setId) {
        return post(userId, "/sets/" + learningId(setId) + "/retry-generation", Map.of(),
                DailyWritingSetResponseDto.class);
    }

    public DailyWritingSetResponseDto regenerate(Long userId, Long setId) {
        return post(userId, "/sets/" + learningId(setId) + "/regenerate", Map.of(),
                DailyWritingSetResponseDto.class);
    }

    public AnswerResultResponseDto submit(Long userId, Long itemId, AnswerSubmitRequestDto request) {
        return post(userId, "/items/" + learningId(itemId) + "/answers", request,
                AnswerResultResponseDto.class);
    }

    public void resume(Long userId, Long itemId) {
        post(userId, "/items/" + learningId(itemId) + "/evaluation/resume", Map.of(), Void.class);
    }

    private <T> T get(Long userId, String path, Class<T> type) {
        return read(http.get().uri(ROOT + path).header(HttpHeaders.AUTHORIZATION, token(userId)), type);
    }

    private <T> T post(Long userId, String path, Object value, Class<T> type) {
        // BE는 선택한 모드와 답변만 직렬화하고 학습 문맥은 LL이 준비한다.
        byte[] body;
        try {
            body = json.writeValueAsBytes(value);
        } catch (JsonProcessingException failure) {
            throw contractFailure();
        }
        return read(http.post().uri(ROOT + path).header(HttpHeaders.AUTHORIZATION, token(userId))
                .contentType(MediaType.APPLICATION_JSON).body(body), type);
    }

    private <T> T read(RestClient.RequestHeadersSpec<?> request, Class<T> type) {
        try {
            return request.exchange((sent, response) -> {
                checkError(response.getStatusCode().value(), response.getBody());
                if (type == Void.class) return null;
                byte[] bytes = response.getBody().readNBytes(MAX_BODY + 1);
                if (bytes.length == 0 || bytes.length > MAX_BODY) throw contractFailure();
                try {
                    T value = json.readValue(bytes, type);
                    if (value == null) throw contractFailure();
                    return value;
                } catch (IOException | IllegalArgumentException failure) {
                    throw contractFailure();
                }
            });
        } catch (RestClientException failure) {
            throw new LanguageLearningServiceException(HttpStatus.BAD_GATEWAY, "LL_WRITING_UNAVAILABLE",
                    "Writing 서비스에 연결하지 못했거나 제한 시간이 지났습니다. 같은 세트를 다시 조회해 주세요.", failure);
        }
    }

    private void checkError(int status, java.io.InputStream body) throws IOException {
        if (status >= 200 && status < 300) return;
        byte[] bytes = body.readNBytes(65537);
        InternalApiErrorDto error = null;
        if (bytes.length <= 65536) try {
            error = json.readValue(bytes, InternalApiErrorDto.class);
        } catch (IOException ignored) {
            // 잘못된 오류 본문은 고정 계약 오류로만 처리한다.
        }
        HttpStatus resolved = HttpStatus.resolve(status);
        if (resolved == null || status < 400) resolved = HttpStatus.BAD_GATEWAY;
        throw new LanguageLearningServiceException(resolved,
                error == null || error.code() == null ? "LL_WRITING_REQUEST_FAILED" : error.code(),
                error == null || error.message() == null ? "Writing 서비스 요청이 실패했습니다." : error.message());
    }

    private String token(Long userId) {
        return "Bearer " + jwt.issueUserToken(positive(userId));
    }

    private static Long positive(Long value) {
        if (value == null || value <= 0) throw new IllegalArgumentException("양수 식별자가 필요합니다.");
        return value;
    }

    private static Long learningId(Long value) {
        // 양수 옛 ID도 LL에 전달해 명시적인 재개 불가 오류를 반환받는다.
        if (value == null || value == 0) throw new IllegalArgumentException("학습 식별자가 필요합니다.");
        return value;
    }

    private static LanguageLearningServiceException contractFailure() {
        return new LanguageLearningServiceException(HttpStatus.BAD_GATEWAY, "LL_WRITING_CONTRACT_ERROR",
                "Writing 서비스 응답 계약이 올바르지 않습니다.");
    }
}
