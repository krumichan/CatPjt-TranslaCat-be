package jp.co.translacat.domain.novelgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.infrastructure.novel.client.NovelGatewayException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NovelReaderControllerTest {
    @Test
    void cancellationUsesTheSamePublicEnvelopeAndNoStorePolicy() throws Exception {
        // 준비: 취소는 번역 결과를 삭제하지 않는 별도 명령이다.
        var facade = mock(NovelReaderFacade.class);
        var mvc = MockMvcBuilders.standaloneSetup(new NovelReaderController(facade))
                .setControllerAdvice(new NovelReaderExceptionAdvice()).build();

        // 실행 및 검증
        mvc.perform(post("/api/v1/syosyetu/n123aa/episodes/1/translations/job_1/cancel"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.resultCode").value(200));
        verify(facade).cancel(new NovelReaderAddress("syosyetu", "n123aa", "1"), "job_1");
    }

    @Test
    void preservesPublicEnvelopeAndDisablesSharedCaching() throws Exception {
        // 준비
        var facade = mock(NovelReaderFacade.class);
        when(facade.reader(any())).thenReturn(new ObjectMapper().readTree("{\"revision\":\"r1\",\"state\":\"SOURCE_READY\"}"));
        var mvc = MockMvcBuilders.standaloneSetup(new NovelReaderController(facade))
                .setControllerAdvice(new NovelReaderExceptionAdvice()).build();

        // 실행 및 검증
        mvc.perform(get("/api/v1/syosyetu/n123aa/episodes/1/reader"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.resultCode").value(200))
                .andExpect(jsonPath("$.body.revision").value("r1"));
    }

    @Test
    void malformedPaidCommandDoesNotCallFacade() throws Exception {
        // 준비
        var facade = mock(NovelReaderFacade.class);
        var mvc = MockMvcBuilders.standaloneSetup(new NovelReaderController(facade))
                .setControllerAdvice(new NovelReaderExceptionAdvice()).build();

        // 실행 및 검증
        mvc.perform(post("/api/v1/syosyetu/n123aa/episodes/1/audio").contentType("application/json")
                        .content("{\"revision\":\"r1\",\"segmentId\":\"s1\",\"language\":\"gemini\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.body.code").value("NOVEL_REQUEST_INVALID"));
        verifyNoInteractions(facade);
    }

    @Test
    void backendFailureRemainsExplicitWithoutLegacyGenerationFallback() throws Exception {
        // 준비
        var facade = mock(NovelReaderFacade.class);
        when(facade.reader(any())).thenThrow(new NovelGatewayException(503, "NOVEL_SOURCE_UNAVAILABLE", true));
        var mvc = MockMvcBuilders.standaloneSetup(new NovelReaderController(facade))
                .setControllerAdvice(new NovelReaderExceptionAdvice()).build();

        // 실행 및 검증
        mvc.perform(get("/api/v1/syosyetu/n123aa/episodes/1/reader"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.resultCode").value(503))
                .andExpect(jsonPath("$.body.code").value("NOVEL_SOURCE_UNAVAILABLE"));
    }
}
