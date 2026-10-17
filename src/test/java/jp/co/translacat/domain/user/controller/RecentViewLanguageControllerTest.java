package jp.co.translacat.domain.user.controller;

import jp.co.translacat.domain.user.dto.RecentViewSaveRequestDto;
import jp.co.translacat.domain.user.service.RecentViewService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecentViewLanguageControllerTest {
    @Test
    void recentSaveAcceptsLegacyKoreanAndExplicitEnglishWithOptionalTitle() throws Exception {
        // 준비
        var service = mock(RecentViewService.class);
        when(service.save(any(RecentViewSaveRequestDto.class))).thenReturn(true);
        var mvc = MockMvcBuilders.standaloneSetup(new RecentViewController(service)).build();
        String base = "\"platformCode\":\"SYOSETU\",\"type\":\"NOVEL\",\"novelId\":\"n123aa\","
                + "\"title\":\"原題\",\"titleJa\":\"原題\"";

        // 실행
        mvc.perform(post("/api/v1/recent/save").contentType("application/json").content("{" + base + "}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/recent/save").contentType("application/json")
                        .content("{" + base + ",\"targetLanguage\":\"en\",\"titleEn\":\"English title\"}"))
                .andExpect(status().isOk());

        // 검증
        var requests = ArgumentCaptor.forClass(RecentViewSaveRequestDto.class);
        verify(service, times(2)).save(requests.capture());
        assertThat(requests.getAllValues().getFirst().getTargetLanguage()).isEqualTo("ko");
        assertThat(requests.getAllValues().getLast().getTargetLanguage()).isEqualTo("en");
        assertThat(requests.getAllValues().getLast().getTitleEn()).isEqualTo("English title");
    }

    @Test
    void recentSaveRejectsUnsupportedTargetsBeforeAnyWrite() throws Exception {
        // 준비
        var service = mock(RecentViewService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new RecentViewController(service)).build();

        // 실행
        mvc.perform(post("/api/v1/recent/save").contentType("application/json")
                        .content("{\"platformCode\":\"SYOSETU\",\"type\":\"NOVEL\",\"novelId\":\"n123aa\","
                                + "\"title\":\"原題\",\"titleJa\":\"原題\",\"targetLanguage\":\"ja\"}"))
                .andExpect(status().isBadRequest());

        // 검증
        verifyNoInteractions(service);
    }
}
