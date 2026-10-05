package jp.co.translacat.infrastructure.novel.client;

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

@Component
@Order(SecurityProperties.DEFAULT_FILTER_ORDER + 1)
public class NovelReaderRequestLimitFilter extends OncePerRequestFilter {
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getMethod().equals("POST") || !(request.getRequestURI().matches(
                "/api/v1/[^/]+/[^/]+/episodes/[^/]+/(translations(?:/[^/]+/(?:cancel|repair))?|audio|glossary)")
                || request.getRequestURI().matches("/api/v1/[^/]+/novels/catalog(?:/[^/]+(?:/cancel|/items/[^/]+/retry))?"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Spring Security 인가 뒤에 실행한다. Content-Length가 없는 요청도 DTO 파싱 전에 제한한다.
        byte[] body = request.getInputStream().readNBytes(8193);
        if (body.length > 8192) {
            response.setStatus(413);
            response.setContentType("application/json");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write("{\"resultCode\":413,\"message\":\"NOVEL_REQUEST_TOO_LARGE\","
                    + "\"body\":{\"code\":\"NOVEL_REQUEST_TOO_LARGE\",\"retryable\":false}}");
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public ServletInputStream getInputStream() {
                var input = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    public int read() { return input.read(); }
                    public boolean isFinished() { return input.available() == 0; }
                    public boolean isReady() { return true; }
                    public void setReadListener(ReadListener listener) {
                        throw new UnsupportedOperationException("Asynchronous request bodies are not supported.");
                    }
                };
            }
        }, response);
    }
}
