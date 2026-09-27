package jp.co.translacat.global.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ApiLoggingFilterTest {

    @Test
    void writingAnswerAndEvaluationBodiesAreNeverLogged() throws Exception {
        // 준비: Writing 답변 요청과 평가 응답에 구별 가능한 합성 원문을 넣는다.
        var request = new MockHttpServletRequest("POST",
                "/api/v1/language-learning/writing/daily/items/1/answers");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent("{\"answer\":\"synthetic-private-answer\"}".getBytes(StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();
        Logger logger = (Logger) LoggerFactory.getLogger(ApiLoggingFilter.class);
        Level previousLevel = logger.getLevel();
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        User.withUsername("synthetic-writer@example.test")
                                .password("unused").authorities("ROLE_USER").build(),
                        null,
                        java.util.List.of()
                )
        );

        // 실행: 실제 필터의 요청·응답 로그 분기를 지난다.
        try {
            new ApiLoggingFilter().doFilter(request, response, (ignoredRequest, servletResponse) -> {
                servletResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
                servletResponse.getWriter().write("{\"originText\":\"synthetic-private-origin\"}");
            });
        } finally {
            SecurityContextHolder.clearContext();
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }

        // 검증: API 응답은 유지하고 서버 로그에는 본문을 남기지 않는다.
        String logged = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
        assertThat(logged).contains("WRITING_REDACTED");
        assertThat(logged).doesNotContain(
                "synthetic-private-answer", "synthetic-private-origin", "synthetic-writer@example.test");
        assertThat(response.getContentAsString()).contains("synthetic-private-origin");
    }

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
