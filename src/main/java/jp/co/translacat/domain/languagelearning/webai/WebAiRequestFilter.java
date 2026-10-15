package jp.co.translacat.domain.languagelearning.webai;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
@Order(SecurityProperties.DEFAULT_FILTER_ORDER + 1)
public class WebAiRequestFilter extends OncePerRequestFilter {
    static final int BODY_LIMIT = 2 * 1024 * 1024;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/language-learning/web-ai/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        if (!Set.of("POST", "PUT", "PATCH").contains(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        // Content-Length가 없는 요청도 MVC 파싱 전에 제한하며 계획의 문제 수에는 상한을 두지 않는다.
        byte[] body = request.getInputStream().readNBytes(BODY_LIMIT + 1);
        if (body.length > BODY_LIMIT) {
            response.setStatus(413);
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"resultCode\":413,\"message\":\"Web AI 요청 본문이 너무 큽니다.\","
                    + "\"body\":{\"errorCode\":\"WEB_AI_REQUEST_TOO_LARGE\"}}");
            return;
        }

        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public ServletInputStream getInputStream() {
                ByteArrayInputStream input = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    @Override
                    public int read() {
                        return input.read();
                    }

                    @Override
                    public boolean isFinished() {
                        return input.available() == 0;
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setReadListener(ReadListener listener) {
                        throw new UnsupportedOperationException("Asynchronous request bodies are not supported.");
                    }
                };
            }

            @Override
            public BufferedReader getReader() {
                return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
            }
        }, response);
    }
}
