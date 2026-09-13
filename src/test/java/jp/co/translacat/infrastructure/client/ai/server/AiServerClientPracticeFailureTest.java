package jp.co.translacat.infrastructure.client.ai.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiPracticeGenerationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiPracticeGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.global.exception.AiServerCommunicationException;
import jp.co.translacat.global.exception.ExternalApiInvocationException;
import jp.co.translacat.infrastructure.client.legacy.ExternalApiClient;
import jp.co.translacat.infrastructure.client.legacy.ExternalApiClient4xxException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiServerClientPracticeFailureTest {

    @Test
    void practiceCallUsesDedicatedCircuitAndPreservesSafeClassification() {
        ExternalApiClient external = mock(ExternalApiClient.class);
        AiServerClient client = new AiServerClient(external, new ObjectMapper());
        ReflectionTestUtils.setField(client, "aiServerUrl", "http://localhost:8000");
        ReflectionTestUtils.setField(client, "apiKey", "secret");
        var circuitOpen = new ExternalApiInvocationException(
                "raw downstream detail",
                mock(CallNotPermittedException.class)
        );
        doThrow(circuitOpen).when(external).postOnceLanguageLearningPractice(
                anyString(), any(), anyMap(), eq(AiPracticeGenerationResponseDto.class)
        );

        assertThatThrownBy(() -> client.callLanguageLearningPracticeGeneration(request()))
                .isInstanceOf(AiServerCommunicationException.class)
                .satisfies(failure -> {
                    AiServerCommunicationException classified =
                            (AiServerCommunicationException) failure;
                    assertThat(classified.getErrorCode()).isEqualTo("CIRCUIT_OPEN");
                    assertThat(classified.isRetryable()).isTrue();
                    assertThat(classified.getMessage()).doesNotContain("raw downstream detail");
                });
        verify(external).postOnceLanguageLearningPractice(
                anyString(), any(), anyMap(), eq(AiPracticeGenerationResponseDto.class)
        );
        verify(external, never()).postOnce(
                anyString(), any(), anyMap(), eq(AiPracticeGenerationResponseDto.class)
        );
    }

    @Test
    void practice422PreservesStatusAndSafePydanticDetailWithoutInputContent() {
        ExternalApiClient external = mock(ExternalApiClient.class);
        AiServerClient client = new AiServerClient(external, new ObjectMapper());
        ReflectionTestUtils.setField(client, "aiServerUrl", "http://localhost:8000");
        ReflectionTestUtils.setField(client, "apiKey", "secret");
        String responseBody = """
                {"detail":[{"type":"missing","loc":["body","previousQuestions",0,"options"],
                "msg":"Field required","input":{"targetExpression":"安全な導入"}}]}
                """;
        var response = WebClientResponseException.create(
                422,
                "Unprocessable Entity",
                HttpHeaders.EMPTY,
                responseBody.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );
        doThrow(new ExternalApiInvocationException(
                "downstream body must remain private",
                new ExternalApiClient4xxException(response)
        )).when(external).postOnceLanguageLearningPractice(
                anyString(), any(), anyMap(), eq(AiPracticeGenerationResponseDto.class)
        );

        assertThatThrownBy(() -> client.callLanguageLearningPracticeGeneration(request()))
                .isInstanceOf(AiServerCommunicationException.class)
                .satisfies(failure -> {
                    AiServerCommunicationException classified =
                            (AiServerCommunicationException) failure;
                    assertThat(classified.getErrorCode()).isEqualTo("HTTP_4XX");
                    assertThat(classified.isRetryable()).isFalse();
                    assertThat(classified.getHttpStatus()).isEqualTo(422);
                    assertThat(classified.getSafeDetail())
                            .isEqualTo("type=missing loc=body.previousQuestions.0.options msg=Field required")
                            .doesNotContain("安全な導入", "input");
                });
    }

    private AiPracticeGenerationRequestDto request() {
        return new AiPracticeGenerationRequestDto(
                "practice-item-1-token",
                PracticeDomain.READING,
                "COMPREHENSION",
                "ko",
                "ja",
                1,
                3,
                0,
                1,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                0,
                LocalDate.of(2026, 9, 13),
                List.of()
        );
    }
}
