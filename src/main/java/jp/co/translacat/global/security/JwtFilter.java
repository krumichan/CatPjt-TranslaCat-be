package jp.co.translacat.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JwtFilter 클래스
 *
 * Spring Security 필터로, 모든 HTTP 요청마다 JWT 토큰을 검증하고
 * SecurityContext에 인증 정보를 설정합니다.
 *
 * OncePerRequestFilter를 상속하여 요청당 한 번만 실행됩니다.
 */
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JWTService jwtService;

    private final ApplicationContext context;

    /**
     * HTTP 요청 필터링
     *
     * @param request     HTTP 요청
     * @param response    HTTP 응답
     * @param filterChain 필터 체인
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        // 1. Authorization 헤더에서 JWT 토큰 추출
        String authorizationHeader = request.getHeader("Authorization");
        String token = null;
        String username = null;

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            token = authorizationHeader.substring(7); // "Bearer " 제거
            username = jwtService.extractUsername(token);
        }

        // 2. SecurityContext에 인증 정보가 없으면 JWT 검증 수행
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // UserDetailsService를 통해 사용자 정보 로드
            UserDetails userDetails = context.getBean(MyUserDetailsService.class).loadUserByUsername(username);

            // JWT 토큰 유효성 검증
            if (jwtService.validateToken(token, userDetails)) {

                // UsernamePasswordAuthenticationToken 생성 (Spring Security 인증 객체)
                UsernamePasswordAuthenticationToken authenticationToken =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                // 추가 정보(request 등) 설정
                authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // SecurityContext에 인증 정보 저장
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            }
        }

        // 다음 필터로 요청 전달
        filterChain.doFilter(request, response);
    }
}
