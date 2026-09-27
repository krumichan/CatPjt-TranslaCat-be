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
    void disabledRemoteStartsAndReturnsExplicitUnavailableForBothReads() {
        // 준비: 기존 test profile처럼 LL 원격 연결을 켜지 않은 실제 Spring 조립 경계다.
        var runner = new ApplicationContextRunner()
                .withUserConfiguration(OverviewClientConfiguration.class, RemoteOverviewGateway.class);

        // 실행: 외부 Controller가 의존하는 Gateway가 연결 설정 없이도 생성된다.
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            var gateway = context.getBean(OverviewGateway.class);
            var single = assertThrows(LanguageLearningServiceException.class,
                    () -> gateway.get(123L, "/dashboard", Map.of(), Object.class));
            var list = assertThrows(LanguageLearningServiceException.class,
                    () -> gateway.list(123L, "/history", Map.of(), Object.class));

            // 검증: 빈 성공 응답이나 이전 Core 경로 대신 동일한 503 계약을 반환한다.
            for (var error : new LanguageLearningServiceException[]{single, list}) {
                assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
                assertEquals("LL_OVERVIEW_REMOTE_DISABLED", error.getErrorCode());
            }
            assertFalse(context.containsBean("languageLearningOverviewClient"));
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
