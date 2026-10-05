package jp.co.translacat.infrastructure.languagelearning.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.languagelearning.common.enums.DailyWritingType;
import jp.co.translacat.infrastructure.languagelearning.client.config.LanguageLearningClientProperties;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.Base64;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LanguageLearningCuratedWritingClientTest {
    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://ll.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final LanguageLearningWritingClient client = client();

    private LanguageLearningWritingClient client() {
        var properties = new LanguageLearningClientProperties();
        properties.getInternalJwt().setSecretBase64(Base64.getEncoder().encodeToString(new byte[32]));
        return new LanguageLearningWritingClient(builder.build(),
                new LanguageLearningInternalJwtProvider(properties, Clock.systemUTC()),
                new ObjectMapper().findAndRegisterModules());
    }

    private String set(int count) {
        var items = IntStream.rangeClosed(1, count).mapToObj(number -> "{\"itemId\":" + number + "}")
                .reduce((left, right) -> left + "," + right).orElse("");
        return "{\"policyVersion\":\"curated-writing-v1\",\"resultPolicy\":\"REFERENCE_ONLY\",\"sentenceCount\":5,\"generatedItemCount\":5,\"items\":["
                + items + "]}";
    }

    @Test
    void curatedStartForwardsExplicitModeAndRejectsPartialAsSuccess() {
        // 준비: 신규 경로는 인증 사용자 ID와 명시 재학습 값만 보낸다.
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/writing/curated/sets"))
                .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Bearer ")))
                .andExpect(content().json("{\"writingType\":\"FREE\",\"rePractice\":false}", true))
                .andRespond(withSuccess(set(5), MediaType.APPLICATION_JSON));

        // 실행: 완성된 비점수 세트를 받는다.
        var result = client.curatedStart(123L, DailyWritingType.FREE, false);

        // 검증: 정책과 다섯 개의 전체 구성을 유지한다.
        assertEquals("REFERENCE_ONLY", result.path("resultPolicy").asText());
        assertEquals(5, result.path("items").size());
        server.verify();
    }

    @Test
    void curatedPartialOrUnknownPolicyIsContractFailure() {
        // 준비: LL이 부분 세트를 성공으로 잘못 반환한 상황을 재현한다.
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/writing/curated/sets/7"))
                .andRespond(withSuccess(set(2), MediaType.APPLICATION_JSON));

        // 실행·검증: BE는 두 문항을 정상 신규 세트로 전달하지 않는다.
        var failure = assertThrows(LanguageLearningServiceException.class, () -> client.curatedGet(123L, 7L));
        assertEquals("LL_WRITING_CONTRACT_ERROR", failure.getErrorCode());
        server.verify();
    }
}
