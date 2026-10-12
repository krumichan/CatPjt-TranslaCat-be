package jp.co.translacat.domain.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jp.co.translacat.domain.support.dto.SupportUserResponseDto;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.global.logging.ApiLoggingFilter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SupportPrivacyTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void missingDisplayNameUsesPublicId(String name) {
        // 준비
        User user = User.createLocalUser("private@test.invalid", "test", name, Role.USER, "TCAT-PUBLIC");

        // 실행
        String displayName = SupportUserResponseDto.displayName(user);

        // 검증: 공지 작성자 이름에 이메일을 대체 표시하지 않는다.
        assertThat(displayName).isEqualTo("TCAT-PUBLIC");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/support/inquiries",
            "/api/v1/support/inquiries/1/replies",
            "/api/v1/support/messages/1",
            "/api/v1/support/recipients",
            "/api/v1/support/notifications"
    })
    void privateBodiesStayOutOfApplicationLogs(String path) throws Exception {
        // 준비: 구별 가능한 합성 본문과 이메일이 로그에 유출되는지 확인한다.
        var request = new MockHttpServletRequest("POST", path);
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent("{\"content\":\"synthetic-private-question\"}".getBytes(StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();
        Logger logger = (Logger) LoggerFactory.getLogger(ApiLoggingFilter.class);
        Level previous = logger.getLevel();
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);

        // 실행
        try {
            new ApiLoggingFilter().doFilter(request, response, (req, res) -> {
                assertThat(req).isSameAs(request);
                assertThat(res).isSameAs(response);
                res.getWriter().write("{\"email\":\"synthetic-private@test.invalid\"}");
            });
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previous);
            appender.stop();
        }

        // 검증: 응답은 유지하고 서버 로그에는 경로/상태만 기록한다.
        String logged = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
        assertThat(logged).contains("SUPPORT_REDACTED");
        assertThat(logged).doesNotContain("synthetic-private-question", "synthetic-private@test.invalid");
        assertThat(response.getContentAsString()).contains("synthetic-private@test.invalid");
    }
}
