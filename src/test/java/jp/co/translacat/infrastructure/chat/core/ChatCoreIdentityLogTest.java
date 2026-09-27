package jp.co.translacat.infrastructure.chat.core;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jp.co.translacat.global.logging.ApiLoggingFilter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ChatCoreIdentityLogTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "/internal/v1/chat/identity",
            "/api/v1/chat/rooms/1/messages",
            "/api/v1/admin/chat/ai/profiles",
            "/ws/chat"
    })
    void identitySubjectAndServiceHeadersAreNotLogged(String path) throws Exception {
        // 준비
        var request = new MockHttpServletRequest("POST", path);
        request.setContentType("application/json");
        request.setContent("{\"subject\":\"synthetic-private@example.invalid\"}".getBytes(StandardCharsets.UTF_8));
        request.addHeader("Authorization", "Bearer synthetic-service-token");
        request.addHeader("X-Chat-Service-Authorization", "Bearer synthetic-ingress-token");
        var response = new MockHttpServletResponse();
        Logger logger = (Logger) LoggerFactory.getLogger(ApiLoggingFilter.class);
        Level previous = logger.getLevel();
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);

        // 실행
        try {
            new ApiLoggingFilter().doFilter(request, response, (ignored, output) ->
                    output.getWriter().write("{\"email\":\"synthetic-private@example.invalid\"}"));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previous);
            appender.stop();
        }

        // 검증
        String log = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + right);
        assertThat(log).contains(path.startsWith("/internal/") ? "CHAT_INTERNAL_REDACTED" : "CHAT_REDACTED")
                .doesNotContain("synthetic-private@example.invalid", "synthetic-service-token",
                        "synthetic-ingress-token");
        assertThat(response.getContentAsString()).contains("synthetic-private@example.invalid");
    }
}
