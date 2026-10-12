package jp.co.translacat.infrastructure.languagelearning.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.languagelearning.common.enums.LearningActivityStatus;
import jp.co.translacat.infrastructure.languagelearning.client.config.LanguageLearningClientProperties;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class WritingPlanActivityClientTest {
    @Test
    void forwardsDeletionRevisionAndRejectsAnotherPlanInResponse() throws Exception {
        // 준비: 원격 삭제 요청은 인증된 owner와 원래 revision만 전달한다.
        var json = new ObjectMapper();
        var builder = RestClient.builder().baseUrl("http://ll.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        var properties = new LanguageLearningClientProperties();
        properties.getInternalJwt().setSecretBase64(Base64.getEncoder().encodeToString(new byte[32]));
        var client = new LanguageLearningWritingClient(builder.build(),
                new LanguageLearningInternalJwtProvider(properties, Clock.systemUTC()), json);
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/writing/curated/plans/-7/delete"))
                .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Bearer ")))
                .andExpect(content().json("{\"planRevision\":2}", true))
                .andRespond(withSuccess("{\"dailySetId\":-7,\"deleted\":true}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://ll.test/internal/v1/language-learning/writing/curated/plans/-8/delete"))
                .andRespond(withSuccess("{\"dailySetId\":-7,\"deleted\":true}", MediaType.APPLICATION_JSON));

        // 실행 / 검증: 다른 계획 삭제 확인은 성공으로 중계하지 않는다.
        assertTrue(client.writingPlanDelete(123L, -7L, json.readTree("{\"planRevision\":2}")).path("deleted").booleanValue());
        assertThrows(LanguageLearningServiceException.class,
                () -> client.writingPlanDelete(123L, -8L, json.readTree("{\"planRevision\":2}")));
        server.verify();
    }

    @Test
    void acceptsNonScoringActivityProgressWithoutEvaluationStatus() throws Exception {
        // 준비 / 실행 / 검증: 진행 중인 학습 활동을 평가 중이나 완료로 변환하지 않는다.
        assertEquals(LearningActivityStatus.IN_PROGRESS,
                new ObjectMapper().readValue("\"IN_PROGRESS\"", LearningActivityStatus.class));
    }
}
