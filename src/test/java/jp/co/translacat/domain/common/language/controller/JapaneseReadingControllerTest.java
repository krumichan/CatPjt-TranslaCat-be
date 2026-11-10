package jp.co.translacat.domain.common.language.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.global.logging.ApiLoggingFilter;
import jp.co.translacat.infrastructure.japanese.SudachiFuriganaProcessor;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class JapaneseReadingControllerTest {
    private static final String PATH = "/api/v1/text-readings/japanese";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void usesRealSudachiAndPreservesUtf16OffsetsWithoutLoggingChatText() throws Exception {
        // 준비: 실제 사전과 운영 로그 필터를 사용하고 이모지 뒤의 한자 위치를 확인한다.
        var sudachi = new SudachiFuriganaProcessor();
        ReflectionTestUtils.setField(sudachi, "dictPath", "src/main/resources/system_full.dic");
        sudachi.init();
        var mvc = MockMvcBuilders.standaloneSetup(new JapaneseReadingController(mapper, sudachi))
                .addFilters(new ApiLoggingFilter()).build();
        String privateText = " 🔒東京\nprivate-chat-fixture";

        // 루트 로그 설정과 상관없이 필터가 작성한 실제 이벤트를 검사한다.
        Logger logger = (Logger) LoggerFactory.getLogger(ApiLoggingFilter.class);
        Level previousLevel = logger.getLevel();
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);

        // 실행 / 검증: 응답 순서와 원문 위치를 유지하고 읽을 한자가 없는 항목은 비워 둔다.
        try {
            mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsBytes(Map.of("texts", List.of(privateText, "こんにちは")))))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.body[0][0].startOffset").value(3))
                    .andExpect(jsonPath("$.body[0][0].surface").value("東京"))
                    .andExpect(jsonPath("$.body[0][0].reading").value("とうきょう"))
                    .andExpect(jsonPath("$.body[1]").isEmpty());
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
        String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .collect(java.util.stream.Collectors.joining("\n"));
        assertThat(logs).contains("TEXT_READINGS_REDACTED")
                .doesNotContain("private-chat-fixture", "とうきょう");
    }

    @Test
    void rejectsOversizedAndMalformedBatchesBeforeCallingSudachi() throws Exception {
        // 준비: 바이트 상한·전체 텍스트 상한·잘못된 JSON을 각각 만든다.
        var sudachi = mock(SudachiFuriganaProcessor.class);
        var mvc = MockMvcBuilders.standaloneSetup(new JapaneseReadingController(mapper, sudachi)).build();
        String longText = "字".repeat(3_000);
        byte[] oversizedBatch = mapper.writeValueAsBytes(Map.of("texts", List.of(longText, longText, longText)));

        // 실행 / 검증: 제한 위반은 형태소 분석 전에 끝난다.
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(" ".repeat(65_537)))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(oversizedBatch))
                .andExpect(status().isBadRequest());
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"texts\":[\"東京\"]} {}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(sudachi);
    }

    @Test
    void tokenizerFailureFallsBackForOnlyTheFailedText() throws Exception {
        // 준비
        var sudachi = mock(SudachiFuriganaProcessor.class);
        when(sudachi.tokenizeWithReadings("大阪")).thenThrow(new IllegalStateException("fixture"));
        when(sudachi.tokenizeWithReadings("東京")).thenReturn(
                List.of(new SudachiFuriganaProcessor.ReadingToken("東京", "とうきょう")));
        var mvc = MockMvcBuilders.standaloneSetup(new JapaneseReadingController(mapper, sudachi)).build();

        // 실행 / 검증
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"texts\":[\"大阪\",\"東京\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body[0]").isEmpty())
                .andExpect(jsonPath("$.body[1][0].reading").value("とうきょう"));
    }
}
