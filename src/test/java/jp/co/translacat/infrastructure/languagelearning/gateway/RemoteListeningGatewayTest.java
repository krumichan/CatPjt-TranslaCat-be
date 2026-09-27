package jp.co.translacat.infrastructure.languagelearning.gateway;

import jp.co.translacat.domain.languagelearning.listening.port.ListeningGateway;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningListeningClient;
import jp.co.translacat.infrastructure.languagelearning.client.LanguageLearningServiceException;
import jp.co.translacat.infrastructure.languagelearning.client.config.ListeningClientConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RemoteListeningGatewayTest {
    @Test
    void disabledRemoteStartsAndRejectsEveryEntryPointWithoutLegacyFallback() {
        // 준비: test profile과 같은 원격 비활성 설정에서 실제 Spring 빈을 조립한다.
        var runner = new ApplicationContextRunner()
                .withUserConfiguration(ListeningClientConfiguration.class, RemoteListeningGateway.class);

        // 실행: 조회·변경·오디오 진입점의 비활성 처리를 함께 확인한다.
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            var gateway = context.getBean(ListeningGateway.class);
            List<Executable> calls = List.of(
                    () -> gateway.get(123L, "/sets/today", Object.class),
                    () -> gateway.list(123L, "/profiles", Object.class),
                    () -> gateway.post(123L, "/sessions", Map.of(), Object.class),
                    () -> gateway.audio(123L, "/audio/synthetic"));

            // 검증: 모든 진입점이 명시적인 503으로 끝나며 원격 전송 빈도 생성하지 않는다.
            for (var call : calls) {
                var error = assertThrows(LanguageLearningServiceException.class, call);
                assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
                assertEquals("LL_LISTENING_REMOTE_DISABLED", error.getErrorCode());
            }
            assertFalse(context.containsBean("languageLearningListeningClient"));
        });
    }

    @Test
    void enabledGatewayPreservesRemoteResultAndFailureWithoutRetry() {
        // 준비: 실제 전송 클래스와 Gateway가 함께 있을 때의 우선 빈 및 응답 전달을 확인한다.
        var client = mock(LanguageLearningListeningClient.class);
        var response = new Object();
        var body = Map.of("synthetic", true);
        when(client.post(123L, "/sessions", body, Object.class)).thenReturn(response);
        var failure = new LanguageLearningServiceException(HttpStatus.NOT_FOUND,
                "LISTENING_AUDIO_NOT_FOUND", "합성 오디오 없음");
        when(client.audio(123L, "/audio/synthetic")).thenThrow(failure);
        var runner = new ApplicationContextRunner()
                .withUserConfiguration(RemoteListeningGateway.class)
                .withBean(LanguageLearningListeningClient.class, () -> client);

        // 실행: 업무 요청과 오디오 요청 모두 기존 전송 클라이언트로 전달한다.
        runner.run(context -> {
            var gateway = context.getBean(ListeningGateway.class);
            var actual = gateway.post(123L, "/sessions", body, Object.class);
            var error = assertThrows(LanguageLearningServiceException.class,
                    () -> gateway.audio(123L, "/audio/synthetic"));

            // 검증: 원본 결과·오류가 유지되며 추가 호출이나 숨은 재시도가 없다.
            assertInstanceOf(RemoteListeningGateway.class, gateway);
            assertSame(response, actual);
            assertSame(failure, error);
            verify(client, times(1)).post(123L, "/sessions", body, Object.class);
            verify(client, times(1)).audio(123L, "/audio/synthetic");
            verifyNoMoreInteractions(client);
        });
    }
}
