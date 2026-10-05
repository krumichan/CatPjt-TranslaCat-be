package jp.co.translacat.infrastructure.languagelearning.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.infrastructure.languagelearning.client.config.LanguageLearningClientProperties;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class WritingPlanClientTest {
    @Test
    void forwardsOriginalNumericTypeAndOwnerJwtWithoutRetryOrFallback() throws Exception {
        // 준비: 비정수는 반올림하지 않고 LL의 엄격 입력 거절로 전달한다.
        var json = new ObjectMapper();
        var builder = RestClient.builder().baseUrl("http://ll.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        var properties = new LanguageLearningClientProperties();
        properties.getInternalJwt().setSecretBase64(Base64.getEncoder().encodeToString(new byte[32]));
        var client = new LanguageLearningWritingClient(builder.build(),
                new LanguageLearningInternalJwtProvider(properties, Clock.systemUTC()), json);
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/writing/curated/plans"))
                .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Bearer ")))
                .andExpect(content().json("{\"writingType\":\"FREE\",\"targetItemCount\":2.5}", true))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"WRITING_REQUEST_INVALID\",\"message\":\"invalid\"}"));

        // 실행 / 검증: 응답을 성공으로 바꾸지 않고 요청1회를 그대로 종료한다.
        var failure = assertThrows(LanguageLearningServiceException.class, () -> client.writingPlanStart(123L,
                json.readTree("{\"writingType\":\"FREE\",\"targetItemCount\":2.5}")));
        assertNotNull(failure);
        server.verify();
    }
}
