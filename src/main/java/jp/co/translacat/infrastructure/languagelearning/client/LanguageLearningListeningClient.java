package jp.co.translacat.infrastructure.languagelearning.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jp.co.translacat.domain.languagelearning.listening.audio.model.ListeningAudioObject;
import jp.co.translacat.domain.languagelearning.listening.port.ListeningGateway;
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

public class LanguageLearningListeningClient implements ListeningGateway {
    private static final String ROOT = "/internal/v1/language-learning/listening";
    private final RestClient http;
    private final LanguageLearningInternalJwtProvider jwt;
    private final ObjectMapper json;

    public LanguageLearningListeningClient(RestClient http, LanguageLearningInternalJwtProvider jwt,
                                           ObjectMapper json) {
        this.http = http;
        this.jwt = jwt;
        this.json = json.copy().enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Override
    public <T> T get(Long userId, String path, Class<T> type) {
        return read(http.get().uri(path(path)), userId, false,
                (bytes, contentType) -> decode(bytes, json.getTypeFactory().constructType(type)));
    }

    @Override
    public <T> List<T> list(Long userId, String path, Class<T> type) {
        return read(http.get().uri(path(path)), userId, false,
                (bytes, contentType) -> decode(bytes, json.getTypeFactory().constructCollectionType(List.class, type)));
    }

    @Override
    public <T> T post(Long userId, String path, Object body, Class<T> type) {
        try {
            return read(http.post().uri(path(path)).contentType(MediaType.APPLICATION_JSON)
                            .body(json.writeValueAsBytes(body == null ? Map.of() : body)), userId, false,
                    (bytes, contentType) -> decode(bytes, json.getTypeFactory().constructType(type)));
        } catch (IOException failure) {
            throw contractFailure();
        }
    }

    @Override
    public ListeningAudioObject audio(Long userId, String path) {
        return read(http.get().uri(path(path)), userId, true, (bytes, contentType) -> {
            if (contentType == null || !"audio".equals(contentType.getType())) throw contractFailure();
            return new ListeningAudioObject(null, bytes, contentType.toString());
        });
    }

    private <T> T read(RestClient.RequestHeadersSpec<?> request, Long userId, boolean audio, Decoder<T> decoder) {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("사용자 식별자가 필요합니다.");
        try {
            // 사용자 JWT와 정해진 LL 경로를 사용하고 네트워크 실패를 Core 업무로 재실행하지 않는다.
            return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.issueUserToken(userId))
                    .exchange((sent, response) -> {
                        int status = response.getStatusCode().value();
                        int limit = status >= 400 ? 65_536 : audio ? 20_000_000 : 4 * 1024 * 1024;
                        byte[] bytes = response.getBody().readNBytes(limit + 1);
                        if (bytes.length > limit || bytes.length == 0) throw contractFailure();
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

                        // 오디오와 JSON 응답을 각 계약으로 읽으며 오류 응답을 빈 성공 DTO로 바꾸지 않는다.
                        try {
                            return decoder.decode(bytes, response.getHeaders().getContentType());
                        } catch (IOException | IllegalArgumentException failure) {
                            throw contractFailure();
                        }
                    });
        } catch (RestClientException failure) {
            throw new LanguageLearningServiceException(HttpStatus.BAD_GATEWAY, "LL_LISTENING_UNAVAILABLE",
                    "Listening 서비스에 연결하지 못했습니다. 같은 학습을 다시 조회해 주세요.", failure);
        }
    }

    private <T> T decode(byte[] bytes, JavaType type) throws IOException {
        if (type.hasRawClass(Void.class)) {
            if (!json.readTree(bytes).isNull()) throw contractFailure();
            return null;
        }
        T value = json.readValue(bytes, type);
        if (value == null) throw contractFailure();
        return value;
    }

    @FunctionalInterface
    private interface Decoder<T> {
        T decode(byte[] bytes, MediaType contentType) throws IOException;
    }

    private String path(String value) {
        if (value == null || !value.startsWith("/") || value.contains(":") || value.contains("..")
                || value.contains("\r") || value.contains("\n"))
            throw new IllegalArgumentException("Listening 요청 경로가 올바르지 않습니다.");
        return ROOT + value;
    }

    private static LanguageLearningServiceException contractFailure() {
        return new LanguageLearningServiceException(HttpStatus.BAD_GATEWAY, "LL_LISTENING_CONTRACT_ERROR",
                "Listening 서비스 응답 계약이 올바르지 않습니다.");
    }
}
