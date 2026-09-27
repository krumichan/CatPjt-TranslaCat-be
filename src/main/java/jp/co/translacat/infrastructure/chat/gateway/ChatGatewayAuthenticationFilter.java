package jp.co.translacat.infrastructure.chat.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

final class ChatGatewayAuthenticationFilter extends OncePerRequestFilter {
    private final ChatGatewayUserAuthenticator authenticator;

    ChatGatewayAuthenticationFilter(ChatGatewayUserAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!ChatGatewayTarget.isChatHttpPath(request.getServletPath())) {
            chain.doFilter(request, response);
            return;
        }

        // 브라우저가 서버 전용 credential을 제출할 수 없다. caller ID/role 헤더도 신뢰하지 않는다.
        var authorization = Collections.list(request.getHeaders("Authorization"));
        var current = authorization.size() == 1 && request.getHeader("X-Chat-Service-Authorization") == null
                ? authenticator.authenticate(authorization.get(0)) : null;
        if (current == null) {
            response.setStatus(401);
            response.setHeader("Cache-Control", "no-store");
            return;
        }

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(current, null, current.getAuthorities()));
        SecurityContextHolder.setContext(context);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
