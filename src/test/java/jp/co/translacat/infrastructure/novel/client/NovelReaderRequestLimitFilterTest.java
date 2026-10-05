package jp.co.translacat.infrastructure.novel.client;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.*;

class NovelReaderRequestLimitFilterTest {
    @Test
    void oversizedBodyIsRejectedBeforeParsingOrPaidExecution() throws Exception {
        // 준비
        var request = new MockHttpServletRequest("POST", "/api/v1/syosyetu/n123aa/episodes/1/translations");
        request.setContent(new byte[8193]);
        var response = new MockHttpServletResponse();

        // 실행
        new NovelReaderRequestLimitFilter().doFilter(request, response, (req, res) -> fail("Must not reach controller"));

        // 검증
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("NOVEL_REQUEST_TOO_LARGE");
    }

    @Test
    void boundedBodyIsPreservedForDtoParser() throws Exception {
        // 준비
        var request = new MockHttpServletRequest("POST", "/api/v1/syosyetu/n123aa/episodes/1/audio");
        byte[] bytes = "{\"revision\":\"r1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        request.setContent(bytes);

        // 실행 및 검증
        new NovelReaderRequestLimitFilter().doFilter(request, new MockHttpServletResponse(),
                (req, res) -> assertThat(req.getInputStream().readAllBytes()).isEqualTo(bytes));
    }
}
