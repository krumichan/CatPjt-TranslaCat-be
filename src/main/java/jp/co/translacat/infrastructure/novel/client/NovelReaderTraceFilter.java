package jp.co.translacat.infrastructure.novel.client;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(SecurityProperties.DEFAULT_FILTER_ORDER - 1)
public class NovelReaderTraceFilter extends OncePerRequestFilter {
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(request.getRequestURI().matches(
                "/api/v1/[^/]+/[^/]+/episodes/[^/]+/(reader|audio|glossary|translations(?:/[^/]+(?:/(?:cancel|repair))?)?)")
                || request.getRequestURI().matches("/api/v1/[^/]+/novels/catalog(?:/[^/]+(?:/cancel|/items/[^/]+/retry)?)?"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // 여기서는 식별자와 시작 시각만 준비한다. 익명/권한 실패의 기존 404 처리를 가로채지 않는다.
        NovelGatewayTrace.forRequest(request);
        chain.doFilter(request, response);
    }
}
