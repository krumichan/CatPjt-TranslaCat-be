package jp.co.translacat.global.exception;

import jp.co.translacat.global.dto.ErrorDto;
import jp.co.translacat.global.dto.RequestContextDto;
import jp.co.translacat.global.dto.ResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionAdviceWritingTest {
    private final ApiExceptionAdvice advice = new ApiExceptionAdvice();

    @Test
    void writingVerifierOutageIsServiceFailureWithSafeCode() {
        var failure = new AiServerCommunicationException(
                "AI Server Language Learning Daily Generation Error",
                "WRITING_VERIFICATION_UNAVAILABLE", false, 503, new RuntimeException("private provider body")
        );
        var result = advice.handleAiServerCommunicationException(failure);
        ResponseDto<ErrorDto> response = result.getBody();
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response).isNotNull();
        assertThat(response.getResultCode()).isEqualTo(503);
        assertThat(response.getBody().getErrorCode()).isEqualTo("WRITING_VERIFICATION_UNAVAILABLE");
        assertThat(response.getMessage()).doesNotContain("private provider body");
    }

    @Test
    void writingCandidateExhaustionIsNotAnInfrastructureFailure() {
        var failure = new AiServerCommunicationException(
                "AI Server Language Learning Daily Generation Error",
                "WRITING_GENERATION_VALIDATION_EXHAUSTED", false, 422, null
        );
        var result = advice.handleAiServerCommunicationException(failure);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(result.getBody().getBody().getErrorCode())
                .isEqualTo("WRITING_GENERATION_VALIDATION_EXHAUSTED");
    }

    @Test
    void writingFailureRetainsExistingRequestTraceId() {
        var request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/language-learning/writing/daily/1/regenerate");
        request.setAttribute("_REQUEST_CONTEXT_VO", new RequestContextDto(
                request.getRequestURI(), "POST", "writing-trace-1", request
        ));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            var failure = new AiServerCommunicationException(
                    "AI Server Language Learning Daily Generation Error",
                    "WRITING_VERIFICATION_UNAVAILABLE", false, 503, null
            );
            var result = advice.handleAiServerCommunicationException(failure);
            assertThat(result.getBody().getGuid()).isEqualTo("writing-trace-1");
            assertThat(result.getBody().getBody().getPath())
                    .isEqualTo("/api/v1/language-learning/writing/daily/1/regenerate");
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
