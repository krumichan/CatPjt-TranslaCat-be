package jp.co.translacat.infrastructure.chat.core;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

final class ChatCoreIdentityFilter extends OncePerRequestFilter {
    private final ChatCoreIdentityTokenVerifier verifier;

    ChatCoreIdentityFilter(ChatCoreIdentityTokenVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // 내부 서비스 토큰 한 개만 받는다. 외부의 X-User-Id/X-Role은 인증 자료가 아니다.
        var headers = Collections.list(request.getHeaders("Authorization"));
        String scope = serviceScope(request);
        Object principal = null;
        if (headers.size() == 1 && headers.get(0).startsWith("Bearer ")) {
            String token = headers.get(0).substring(7);
            principal = scope == null ? verifier.verify(token) : verifier.verifyService(token, scope);
        }
        if (principal == null) {
            response.setStatus(401);
            response.setHeader("Cache-Control", "no-store");
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"CHAT_CORE_AUTH_REQUIRED\"}");
            return;
        }

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority(scope == null ? "chat:identity:read" : scope))));
        SecurityContextHolder.setContext(context);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static String serviceScope(HttpServletRequest request) {
        // method와 경로마다 단일 scope를 요구한다. identity token을 worker 호출에 재사용할 수 없다.
        return switch (request.getMethod() + " " + request.getServletPath()) {
            case "POST /internal/v1/chat/accounts/lookup" -> "chat:accounts:read";
            case "POST /internal/v1/chat/relations/query" -> "chat:relations:read";
            case "POST /internal/v1/chat/storage/urls" -> "chat:storage:read";
            case "PUT /internal/v1/chat/storage/objects" -> "chat:storage:write";
            case "DELETE /internal/v1/chat/storage/objects" -> "chat:storage:delete";
            default -> null;
        };
    }
}
