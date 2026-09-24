package jp.co.translacat.infrastructure.languagelearning.resultjournal;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import java.nio.charset.StandardCharsets;

/** 동일 envelope만 재전달한다. 사용자 토큰, 관리자 토큰, Settings 조회 토큰은 사용하지 않는다. */
public final class HttpResultJournalClient implements ResultJournalClient {
    private final RestClient client;
    private final LanguageLearningInternalJwtProvider jwt;
    private final ObjectMapper mapper;
    public HttpResultJournalClient(RestClient client, LanguageLearningInternalJwtProvider jwt, ObjectMapper mapper) {
        this.client = client; this.jwt = jwt;
        this.mapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    }
    @Override public ResultAcknowledgement deliver(ResultEnvelope envelope) {
        byte[] body;
        try { body = mapper.writeValueAsBytes(envelope); }
        catch (Exception failure) { throw new InvalidAcknowledgement(); }
        return client.post().uri("/internal/v1/learning-results")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.issueLearningResultsToken())
                .contentType(MediaType.APPLICATION_JSON).body(body)
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    if (status >= 300) {
                        // 오류 본문은 읽거나 보관하지 않는다. 분류에 필요한 HTTP 상태만 전달한다.
                        throw new RestClientResponseException("결과 원장 HTTP 요청이 거부되었습니다.", status, "",
                                null, new byte[0], StandardCharsets.UTF_8);
                    }
                    if (status != 200) throw new InvalidAcknowledgement();
                    byte[] bytes = response.getBody().readNBytes(8193);
                    if (bytes.length == 0 || bytes.length > 8192) throw new InvalidAcknowledgement();
                    try {
                        ResultAcknowledgement ack = mapper.readValue(bytes, ResultAcknowledgement.class);
                        if (ack == null || !ack.matches(envelope)) throw new InvalidAcknowledgement();
                        return ack;
                    } catch (Exception failure) { throw new InvalidAcknowledgement(); }
                });
    }
    public static final class InvalidAcknowledgement extends RuntimeException {
        public InvalidAcknowledgement() { super("결과 원장 응답 계약이 일치하지 않습니다."); }
    }
}
