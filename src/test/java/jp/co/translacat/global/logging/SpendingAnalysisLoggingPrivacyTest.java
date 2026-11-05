package jp.co.translacat.global.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SpendingAnalysisLoggingPrivacyTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/account-books/30001/spending-analysis",
            "/api/v1/account-books/30001/spending-analysis/snapshot",
            "/api/v1/account-books/30001/spending-analysis/history",
            "/api/v1/account-books/30001/spending-analysis/history/8cfcb3f0-2018-4000-a1b2-123456789abc"
    })
    void analysisFamilyDoesNotLogCommentsResultsOrIdentityAndDoesNotBufferBodies(String path) throws Exception {
        verifyRedacted(path, "");
    }

    @Test
    void servletResolvedAnalysisPathAlsoRedactsTheRawUri() throws Exception {
        verifyRedacted("/api/v1/account-books/30001/spending%2danalysis/history",
                "/api/v1/account-books/30001/spending-analysis/history");
    }

    private void verifyRedacted(String path, String servletPath) throws Exception {
        // 준비: 코멘트·결과·헤더·사용자명을 구별 가능한 합성 표식으로 만든다.
        var request = new MockHttpServletRequest("POST", path);
        request.setServletPath(servletPath);
        request.setContentType("application/json");
        request.setContent("private-comment".getBytes(StandardCharsets.UTF_8));
        request.addHeader("Authorization", "Bearer private-token");
        var response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("private-user@example.test", null));
        Logger logger = (Logger) LoggerFactory.getLogger(ApiLoggingFilter.class);
        Level previous = logger.getLevel();
        var logs = new ListAppender<ILoggingEvent>();
        logs.start();
        logger.addAppender(logs);
        logger.setLevel(Level.INFO);

        // 실행: 본문을 캐싱하지 않고 기존 요청과 응답을 그대로 전달한다.
        try {
            new ApiLoggingFilter().doFilter(request, response, (req, res) -> {
                assertThat(req).isSameAs(request);
                assertThat(res).isSameAs(response);
                assertThat(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("private-comment");
                response.setStatus(502);
                res.getWriter().write("private-analysis-evidence-response");
            });
        } finally {
            SecurityContextHolder.clearContext();
            logger.detachAppender(logs);
            logger.setLevel(previous);
            logs.stop();
        }

        // 검증: 고정 경로·상태만 기록하며 실제 응답과 오류 상태는 유지한다.
        String text = logs.list.stream().map(ILoggingEvent::getFormattedMessage)
                .collect(java.util.stream.Collectors.joining("\n"));
        assertThat(text).contains("SPENDING_ANALYSIS_REDACTED", "Status: 502")
                .doesNotContain("30001", "private-comment", "private-analysis-evidence-response", "private-token", "private-user@example.test");
        assertThat(response.getStatus()).isEqualTo(502);
        assertThat(response.getContentAsString()).isEqualTo("private-analysis-evidence-response");
    }

    @Test
    void similarlyNamedUnrelatedRouteKeepsItsOriginalLoggingBranch() throws Exception {
        // 준비
        var request = new MockHttpServletRequest("POST", "/api/v1/account-books/30001/spending-analysis-extra");
        request.setContentType("application/json");
        request.setContent("ordinary-body".getBytes(StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();

        // 실행 / 검증: 일반 경로는 기존 wrapper를 계속 사용한다.
        new ApiLoggingFilter().doFilter(request, response, (req, res) -> {
            assertThat(req).isInstanceOf(CustomCachingRequestWrapper.class);
            res.getWriter().write("ordinary-response");
        });
        assertThat(response.getContentAsString()).isEqualTo("ordinary-response");
    }
}
