package jp.co.translacat.global.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ApiLoggingFilterTest {

    @Test
    void authRequestAndResponseBodiesAreNeverLogged() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent("{\"email\":\"user@example.com\",\"password\":\"plain-secret\"}"
                .getBytes(StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();
        Logger logger = (Logger) LoggerFactory.getLogger(ApiLoggingFilter.class);
        Level previousLevel = logger.getLevel();
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);

        try {
            new ApiLoggingFilter().doFilter(request, response, (ignoredRequest, servletResponse) -> {
                servletResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
                servletResponse.getWriter().write("{\"accessToken\":\"token-secret\"}");
            });
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }

        String logged = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
        assertThat(logged).contains("AUTH_REDACTED");
        assertThat(logged).doesNotContain("plain-secret", "token-secret", "user@example.com");
        assertThat(response.getContentAsString()).contains("token-secret");
    }
}
