package jp.co.translacat.infrastructure.languagelearning.gateway;

import jp.co.translacat.domain.languagelearning.dashboard.port.OverviewGateway;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningOverviewClient;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import jp.co.translacat.infrastructure.languagelearning.client.config.OverviewClientConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RemoteOverviewGatewayTest {
    @Test
    void missingRequiredClientFailsStartupEvenWithRetiredFalseSetting() {
        // 준비: 없어진 OFF 값을 전달해도 실제 원격 client 구성을 생략할 수 없다.
        var runner = new ApplicationContextRunner()
                .withPropertyValues("language-learning.remote.enabled=false")
                .withUserConfiguration(OverviewClientConfiguration.class, RemoteOverviewGateway.class);

        // 실행 / 검증: 필수 전송 빈 누락은 비활성 503 상태로 기동하지 않고 시작 시 실패한다.
        runner.run(context -> {
            assertNotNull(context.getStartupFailure());
            assertTrue(context.getStartupFailure().getMessage().contains("languageLearningOverviewClient"));
            assertTrue(context.getStartupFailure().getMessage().contains("RestClient"));
        });
    }

    @Test
    void enabledClientIsSelectedOnceAndRemoteFailureIsPreserved() {
        // 준비: Gateway와 실제 전송 클래스가 함께 존재할 때 우선 빈 선택도 확인한다.
        var client = mock(LanguageLearningOverviewClient.class);
        var query = Map.of("source", "LISTENING");
        var failure = new LanguageLearningServiceException(HttpStatus.BAD_REQUEST,
                "LISTENING_INVALID_STATE", "합성 조회 조건 오류");
        when(client.get(123L, "/dashboard", query, Object.class)).thenThrow(failure);
        var runner = new ApplicationContextRunner()
                .withUserConfiguration(RemoteOverviewGateway.class)
                .withBean(LanguageLearningOverviewClient.class, () -> client);

        // 실행: @Primary 위임 경계가 원격 클라이언트의 오류를 전달한다.
        runner.run(context -> {
            var gateway = context.getBean(OverviewGateway.class);
            var actual = assertThrows(LanguageLearningServiceException.class,
                    () -> gateway.get(123L, "/dashboard", query, Object.class));

            // 검증: 요청 인자·원본 오류를 그대로 유지하고 숨은 재시도를 추가하지 않는다.
            assertInstanceOf(RemoteOverviewGateway.class, gateway);
            assertSame(failure, actual);
            verify(client, times(1)).get(123L, "/dashboard", query, Object.class);
            verifyNoMoreInteractions(client);
        });
    }
}
