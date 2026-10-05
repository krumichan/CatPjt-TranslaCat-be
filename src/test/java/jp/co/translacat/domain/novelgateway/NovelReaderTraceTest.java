package jp.co.translacat.domain.novelgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletResponse;
import jp.co.translacat.infrastructure.novel.client.NovelReaderTraceFilter;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NovelReaderTraceTest {
    @Test
    void returnsMonotonicTimingAndSameTraceWithoutChangingBodyOrExistingExposedHeaders() throws Exception {
        // 준비: 공유 CORS가 이미 노출한 인증 헤더는 그대로 남겨야 한다.
        var facade = mock(NovelReaderFacade.class);
        when(facade.reader(any())).thenReturn(new ObjectMapper().readTree("{\"revision\":\"source-r1\"}"));
        Filter existingCors = (request, response, chain) -> {
            ((HttpServletResponse) response).setHeader("Access-Control-Expose-Headers", "Authorization");
            chain.doFilter(request, response);
        };
        var mvc = MockMvcBuilders.standaloneSetup(new NovelReaderController(facade))
                .addFilters(existingCors, new NovelReaderTraceFilter())
                .setControllerAdvice(new NovelReaderExceptionAdvice(), new NovelReaderTraceAdvice()).build();
        String traceId = "9a4e8097-21d6-4e8f-b764-3e58df753294";

        // 실행
        var response = mvc.perform(get("/api/v1/syosyetu/n2604qf/episodes/1/reader")
                        .header("X-Novel-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Novel-Trace-Id", traceId))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.body.revision").value("source-r1"))
                .andReturn().getResponse();

        // 검증: 별도로 측정하지 않은 upstream 시간은 임의 숫자로 채우지 않는다.
        assertThat(response.getHeaders("X-Novel-Trace-Id")).containsExactly(traceId);
        assertThat(response.getHeaders("Access-Control-Expose-Headers")).hasSize(1);
        assertThat(Double.parseDouble(response.getHeader("X-Novel-Be-Duration-Ms"))).isNotNegative();
        assertThat(response.getHeader("X-Novel-Upstream-Duration-Ms")).isNull();
        assertThat(response.getHeader("Access-Control-Expose-Headers"))
                .contains("Authorization", "X-Novel-Trace-Id", "X-Novel-Be-Duration-Ms");
    }

    @Test
    void doesNotAddNovelTracingToOtherControllers() throws Exception {
        // 준비
        var mvc = MockMvcBuilders.standaloneSetup(new OtherController())
                .addFilters(new NovelReaderTraceFilter())
                .setControllerAdvice(new NovelReaderTraceAdvice()).build();

        // 실행 및 검증
        mvc.perform(get("/unrelated").header("X-Novel-Trace-Id", "untrusted unrelated header"))
                .andExpect(status().isOk()).andExpect(content().string("ok"))
                .andExpect(header().doesNotExist("X-Novel-Trace-Id"))
                .andExpect(header().doesNotExist("X-Novel-Be-Duration-Ms"));
    }

    @RestController
    static class OtherController {
        @GetMapping("/unrelated")
        String read() { return "ok"; }
    }
}
