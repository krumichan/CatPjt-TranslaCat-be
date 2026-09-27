package jp.co.translacat.infrastructure.languagelearning.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jp.co.translacat.domain.languagelearning.speaking.audio.model.SpeakingAudioObject;
import jp.co.translacat.domain.languagelearning.speaking.port.SpeakingGateway;
import jp.co.translacat.infrastructure.languagelearning.client.dto.InternalApiErrorDto;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class LanguageLearningSpeakingClient implements SpeakingGateway {
    private static final String ROOT = "/internal/v1/language-learning/speaking";
    private final RestClient http;
    private final LanguageLearningInternalJwtProvider jwt;
    private final ObjectMapper json;

    public LanguageLearningSpeakingClient(RestClient http, LanguageLearningInternalJwtProvider jwt, ObjectMapper json) {
        this.http = http;
        this.jwt = jwt;
        this.json = json.copy().enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Override
    public <T> T get(Long userId, String path, Class<T> type) {
        return read(http.get().uri(path(path)), userId, false, false,
                (bytes, contentType) -> decode(bytes, json.getTypeFactory().constructType(type), false));
    }

    @Override
    public <T> T optional(Long userId, String path, Class<T> type) {
        return read(http.get().uri(path(path)), userId, false, false,
                (bytes, contentType) -> decode(bytes, json.getTypeFactory().constructType(type), true));
    }

    @Override
    public <T> List<T> list(Long userId, String path, Class<T> type) {
        return read(http.get().uri(path(path)), userId, false, false,
                (bytes, contentType) -> decode(bytes, json.getTypeFactory().constructCollectionType(List.class, type),
                        false));
    }

    @Override
    public <T> T post(Long userId, String path, Object body, Class<T> type) {
        try {
            return read(http.post().uri(path(path)).contentType(MediaType.APPLICATION_JSON)
                            .body(json.writeValueAsBytes(body == null ? Map.of() : body)), userId, false, false,
                    (bytes, contentType) -> decode(bytes, json.getTypeFactory().constructType(type), false));
        } catch (IOException failure) {
            throw contractFailure();
        }
    }

    @Override
    public <T> T adminPatch(Long adminUserId, String path, Object body, Class<T> type) {
        try {
            return read(http.patch().uri(path(path)).contentType(MediaType.APPLICATION_JSON)
                            .body(json.writeValueAsBytes(body)), adminUserId, true, false,
                    (bytes, contentType) -> decode(bytes, json.getTypeFactory().constructType(type), false));
        } catch (IOException failure) {
            throw contractFailure();
        }
    }

    @Override
    public SpeakingAudioObject audio(Long userId, String path) {
        return read(http.get().uri(path(path)), userId, false, true, (bytes, contentType) -> {
            if (contentType == null || !"audio".equals(contentType.getType())) throw contractFailure();
            return new SpeakingAudioObject(null, bytes, contentType.toString());
        });
    }

    private <T> T read(RestClient.RequestHeadersSpec<?> request, Long userId, boolean admin, boolean audio,
                       Decoder<T> decoder) {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("사용자 식별자가 필요합니다.");
        try {
            // 관리자 요청도 실제 인증된 행위자를 서명하며 고정된 내부 서비스 경계만 호출한다.
            String token = admin ? jwt.issueAdminToken(userId) : jwt.issueUserToken(userId);
            return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange((sent, response) -> {
                int status = response.getStatusCode().value();
                int limit = status >= 400 ? 65_536 : audio ? 20_000_000 : 4 * 1024 * 1024;
                byte[] bytes = response.getBody().readNBytes(limit + 1);
                if (bytes.length == 0 || bytes.length > limit) throw contractFailure();
                if (status >= 400) {
                    InternalApiErrorDto error;
                    try {
                        error = json.readValue(bytes, InternalApiErrorDto.class);
                    } catch (IOException failure) {
                        throw contractFailure();
                    }
                    if (error == null || error.code() == null || error.message() == null) throw contractFailure();
                    throw new LanguageLearningServiceException(HttpStatus.valueOf(status), error.code(),
                            error.message());
                }
                if (status < 200 || status >= 300) throw contractFailure();

                // JSON null은 active 조회와 Void 계약에서만 허용하고 잘못된 성공 DTO는 거부한다.
                try {
                    return decoder.decode(bytes, response.getHeaders().getContentType());
                } catch (IOException | IllegalArgumentException failure) {
                    throw contractFailure();
                }
            });
        } catch (RestClientException failure) {
            throw new LanguageLearningServiceException(HttpStatus.BAD_GATEWAY, "LL_SPEAKING_UNAVAILABLE",
                    "Speaking 서비스에 연결하지 못했습니다. 같은 세션을 다시 조회해 주세요.", failure);
        }
    }

    private <T> T decode(byte[] bytes, JavaType type, boolean optional) throws IOException {
        if (type.hasRawClass(Void.class)) {
            if (!json.readTree(bytes).isNull()) throw contractFailure();
            return null;
        }
        T value = json.readValue(bytes, type);
        if (value == null && !optional) throw contractFailure();
        return value;
    }

    @FunctionalInterface
    private interface Decoder<T> {
        T decode(byte[] bytes, MediaType contentType) throws IOException;
    }

    private String path(String value) {
        if (value == null || !value.startsWith("/") || value.contains(":") || value.contains("..")
                || value.contains("\r") || value.contains("\n"))
            throw new IllegalArgumentException("Speaking 요청 경로가 올바르지 않습니다.");
        return ROOT + value;
    }

    private static LanguageLearningServiceException contractFailure() {
        return new LanguageLearningServiceException(HttpStatus.BAD_GATEWAY, "LL_SPEAKING_CONTRACT_ERROR",
                "Speaking 서비스 응답 계약이 올바르지 않습니다.");
    }
}
