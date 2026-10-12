package jp.co.translacat.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JwtFilter 클래스
 * <p>
 * Spring Security 필터로, 모든 HTTP 요청마다 JWT 토큰을 검증하고
 * SecurityContext에 인증 정보를 설정합니다.
 * <p>
 * OncePerRequestFilter를 상속하여 요청당 한 번만 실행됩니다.
 */
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JWTService jwtService;

    private final ApplicationContext context;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Chat 내부 서비스 JWT는 전용 SecurityFilterChain이 검증한다. 외부 사용자 JWT 키와 혼용하지 않는다.
        String path = request.getServletPath();
        return path.equals("/internal/v1/chat") || path.startsWith("/internal/v1/chat/")
                || (path.equals("/ws/chat") || path.startsWith("/ws/chat/")
                || path.equals("/api/v1/chat") || path.startsWith("/api/v1/chat/")
                || path.equals("/api/v1/admin/chat") || path.startsWith("/api/v1/admin/chat/")
                || path.equals("/api/v1/users/me/chat-language-settings"));
    }

    /**
     * HTTP 요청 필터링
     *
     * @param request     HTTP 요청
     * @param response    HTTP 응답
     * @param filterChain 필터 체인
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1. Authorization 헤더에서 JWT 토큰 추출
        String authorizationHeader = request.getHeader("Authorization");
        String token = null;
        String username = null;

        try {
            // 서명·만료·access 용도를 먼저 확인한다. 갱신 토큰으로 보호 API에 접근할 수 없다.
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                token = authorizationHeader.substring(7);
                jwtService.getAccessId(token);
                username = jwtService.extractUsername(token);
            }

            // 현재 DB 사용자를 확인한 뒤에만 인증 주체를 저장한다.
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = context.getBean(MyUserDetailsService.class).loadUserByUsername(username);
                if (jwtService.validateToken(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authenticationToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                }
            }
        } catch (JwtException | IllegalArgumentException | AuthenticationException invalidCredentials) {
            // 토큰 원문이나 파서 예외를 노출하지 않고 기존 경로별 인증 거부 정책으로 넘긴다.
            SecurityContextHolder.clearContext();
        }

        // 다음 필터로 요청 전달
        filterChain.doFilter(request, response);
    }
}
