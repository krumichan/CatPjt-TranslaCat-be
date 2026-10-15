package jp.co.translacat.domain.languagelearning.webai;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class WebAiRequestFilterTest {
    @Test
    void bodyLimitRejectsChunkedOversizeBeforeController() throws Exception {
        // 준비: Content-Length에 의존하지 않고 실제 스트림을 검사한다.
        var request = new MockHttpServletRequest("POST", "/api/v1/language-learning/web-ai/external/results");
        request.setContent(new byte[WebAiRequestFilter.BODY_LIMIT + 1]);
        var response = new MockHttpServletResponse();
        var called = new AtomicBoolean();

        // 실행
        new WebAiRequestFilter().doFilter(request, response, (req, res) -> called.set(true));

        // 검증
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("WEB_AI_REQUEST_TOO_LARGE");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(called).isFalse();
    }

    @Test
    void acceptedBodyRemainsAvailableToJsonParser() throws Exception {
        // 준비
        var request = new MockHttpServletRequest("POST", "/api/v1/language-learning/web-ai/results");
        request.setContent("{\"originalFeedbackMarkdown\":\"日本語 해설\"}".getBytes(StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();

        // 실행 / 검증
        new WebAiRequestFilter().doFilter(request, response, (req, res) -> {
            assertThat(req.getReader().readLine()).contains("日本語 해설");
            assertThat(res).isSameAs(response);
        });
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }
}
