package jp.co.translacat.infrastructure.languagelearning.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.languagelearning.level.dto.request.*;
import jp.co.translacat.domain.languagelearning.level.dto.response.*;
import jp.co.translacat.domain.languagelearning.level.model.*;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import jp.co.translacat.infrastructure.languagelearning.client.dto.InternalApiErrorDto;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 느린 AI 작업용 timeout은 Settings client와 분리한다. HTTP 재전송/옛 DB fallback은 하지 않는다.
 */
public class LanguageLearningLevelTestClient {
    private static final String ROOT = "/internal/v1/language-learning/level-test";
    private static final int MAX_BODY = 32 * 1024 * 1024;
    private final RestClient http;
    private final LanguageLearningInternalJwtProvider jwt;
    private final ObjectMapper json;

    public LanguageLearningLevelTestClient(RestClient http, LanguageLearningInternalJwtProvider jwt,
                                           ObjectMapper json) {
        this.http = http;
        this.jwt = jwt;
        this.json = json.copy()
                .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    private static Long positive(Long value) {
        if (value == null || value <= 0) throw new IllegalArgumentException("양수 식별자가 필요합니다.");
        return value;
    }

    private static String sessionPath(Long id) {
        return "/sessions/" + positive(id);
    }

    private static String itemPath(Long session, Long item) {
        return sessionPath(session) + "/items/" + positive(item);
    }

    private static LanguageLearningServiceException failure(String code, String message) {
        return new LanguageLearningServiceException(HttpStatus.BAD_GATEWAY, code, message);
    }

    private static LanguageLearningServiceException unavailable() {
        return failure("LL_LEVEL_SERVICE_UNAVAILABLE", "레벨 테스트 서비스에 연결하지 못했거나 제한 시간이 지났습니다. 저장 여부는 같은 키로 확인해 주세요.");
    }

    public LevelStatusResponseDto status(Long user) {
        return get(user, "/status", LevelStatusResponseDto.class);
    }

    public LevelSessionResponseDto start(Long user, LevelTestStartRequestDto request) {
        return post(user, "/sessions", request, LevelSessionResponseDto.class);
    }

    public LevelSessionResponseDto session(Long user, Long id) {
        return get(user, sessionPath(id), LevelSessionResponseDto.class);
    }

    public LevelQuestionResponseDto current(Long user, Long id) {
        return get(user, sessionPath(id) + "/current-item", LevelQuestionResponseDto.class);
    }

    public LevelAnswerResultResponseDto submit(Long user, Long session, Long item, LevelAnswerRequestDto request) {
        return post(user, itemPath(session, item) + "/answers", request, LevelAnswerResultResponseDto.class);
    }

    public LevelAudioAnswerResultResponseDto submitAudio(Long user, Long session, Long item, byte[] bytes, String mime,
                                                         Integer duration, String key) {
        if (bytes == null || bytes.length == 0 || bytes.length > 10 * 1024 * 1024)
            throw failure("LL_LEVEL_AUDIO_INVALID", "녹음 크기를 확인해 주세요.");
        return read(http.post()
                .uri(builder -> builder.path(ROOT + itemPath(session, item) + "/answers/audio")
                        .queryParam("durationMs", duration)
                        .queryParam("idempotencyKey", key)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, token(user))
                .contentType(MediaType.parseMediaType(mime))
                .body(bytes), LevelAudioAnswerResultResponseDto.class);
    }

    public LevelAnswerResultResponseDto retry(Long user, Long session, Long item) {
        return post(user, itemPath(session, item) + "/evaluation/retry", java.util.Map.of(),
                LevelAnswerResultResponseDto.class);
    }

    public LevelTestResultResponseDto result(Long user, Long session) {
        return get(user, sessionPath(session) + "/result", LevelTestResultResponseDto.class);
    }

    public List<LevelTestHistoryItemResponseDto> history(Long user) {
        return Arrays.asList(get(user, "/history", LevelTestHistoryItemResponseDto[].class));
    }

    public LevelTestHistoryDetailResponseDto detail(Long user, Long session) {
        return get(user, "/history/" + positive(session), LevelTestHistoryDetailResponseDto.class);
    }

    public Optional<LevelCompletionSnapshot> baseline(Long user) {
        var value = get(user, "/baseline", BaselineResponse.class).baseline();
        if (value != null && !user.equals(value.userId()))
            throw failure("LL_LEVEL_OWNER_MISMATCH", "레벨 기준점의 소유자가 다릅니다.");
        return Optional.ofNullable(value);
    }

    public List<LevelCompletionSnapshot> completions(Long user) {
        var value = get(user, "/completions", CompletionsResponse.class).completions();
        if (value == null || value.stream().anyMatch(v -> v == null || !user.equals(v.userId())))
            throw failure("LL_LEVEL_OWNER_MISMATCH", "레벨 이력의 소유자가 다릅니다.");
        return value;
    }

    public LevelTestAudioData audio(Long user, Long item, String kind) {
        if (!List.of("reference-audio", "answer-audio", "model-answer-audio").contains(kind))
            throw new IllegalArgumentException("음성 종류를 확인해 주세요.");
        try {
            return http.get()
                    .uri(ROOT + "/items/" + positive(item) + "/" + kind)
                    .header(HttpHeaders.AUTHORIZATION, token(user))
                    .exchange((request, response) -> {
                        checkError(response.getStatusCode().value(), response.getBody());
                        byte[] bytes = response.getBody().readNBytes(10 * 1024 * 1024 + 1);
                        if (bytes.length == 0 || bytes.length > 10 * 1024 * 1024)
                            throw failure("LL_LEVEL_AUDIO_INVALID", "음성 응답 크기를 확인해 주세요.");
                        String mime = response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
                        if (mime == null || (!mime.startsWith("audio/")
                                && !mime.startsWith("video/webm")
                                && !mime.startsWith("video/mp4")))
                            throw failure("LL_LEVEL_AUDIO_INVALID", "음성 응답 형식이 올바르지 않습니다.");
                        return new LevelTestAudioData(bytes, mime);
                    });
        } catch (RestClientException error) {
            throw unavailable();
        }
    }

    private <T> T get(Long user, String path, Class<T> type) {
        return read(http.get().uri(ROOT + path).header(HttpHeaders.AUTHORIZATION, token(user)), type);
    }

    private <T> T post(Long user, String path, Object value, Class<T> type) {
        return read(http.post()
                .uri(ROOT + path)
                .header(HttpHeaders.AUTHORIZATION, token(user))
                .contentType(MediaType.APPLICATION_JSON)
                .body(value), type);
    }

    private <T> T read(RestClient.RequestHeadersSpec<?> request, Class<T> type) {
        try {
            return request.exchange((sent, response) -> {
                checkError(response.getStatusCode().value(), response.getBody());
                byte[] bytes = response.getBody().readNBytes(MAX_BODY + 1);
                if (bytes.length == 0 || bytes.length > MAX_BODY)
                    throw failure("LL_LEVEL_RESPONSE_INVALID", "레벨 테스트 응답 크기를 확인해 주세요.");
                try {
                    T value = json.readValue(bytes, type);
                    if (value == null) throw failure("LL_LEVEL_RESPONSE_INVALID", "레벨 테스트 응답이 비어 있습니다.");
                    return value;
                } catch (IOException | IllegalArgumentException error) {
                    throw failure("LL_LEVEL_RESPONSE_INVALID", "레벨 테스트 응답 계약을 확인해 주세요.");
                }
            });
        } catch (RestClientException error) {
            throw unavailable();
        }
    }

    private void checkError(int status, java.io.InputStream body) throws IOException {
        if (status >= 200 && status < 300) return;
        InternalApiErrorDto error = null;
        byte[] bytes = body.readNBytes(65537);
        if (bytes.length <= 65536) try {
            error = json.readValue(bytes, InternalApiErrorDto.class);
        } catch (IOException ignored) {
        }
        HttpStatus resolved = HttpStatus.resolve(status);
        if (resolved == null || status < 400) resolved = HttpStatus.BAD_GATEWAY;
        throw new LanguageLearningServiceException(resolved,
                error == null || error.code() == null ? "LL_LEVEL_REQUEST_FAILED" : error.code(),
                error == null || error.message() == null ? "레벨 테스트 서비스 요청이 실패했습니다." : error.message());
    }

    private String token(Long user) {
        return "Bearer " + jwt.issueUserToken(positive(user));
    }

    public record BaselineResponse(LevelCompletionSnapshot baseline) {
    }

    public record CompletionsResponse(List<LevelCompletionSnapshot> completions) {
    }
}
