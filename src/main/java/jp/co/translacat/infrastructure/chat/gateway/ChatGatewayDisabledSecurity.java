package jp.co.translacat.infrastructure.chat.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "chat.gateway", name = "enabled", havingValue = "false", matchIfMissing = true)
public class ChatGatewayDisabledSecurity {
    @Bean
    @Order(2)
    SecurityFilterChain chatGatewayDisabledSecurityFilterChain(HttpSecurity http) throws Exception {
        // 설정이 없거나 명시적으로 차단한 경우도 Chat 경로를 소유한다. 옛 업무나 일반 MVC로 넘기지 않는다.
        return http.securityMatcher("/api/v1/chat/**", "/api/v1/admin/chat/**",
                        "/api/v1/users/me/chat-language-settings", "/ws/chat", "/ws/chat/**")
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .addFilterAfter(new DisabledResponseFilter(), AuthorizationFilter.class)
                .build();
    }

    private static final class DisabledResponseFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws IOException {
            // WebSocket upgrade 역시 503으로 끝낸다. 계정 조회·DB 접근·upstream 연결은 시작하지 않는다.
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setHeader("Cache-Control", "no-store");
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"CHAT_GATEWAY_DISABLED\"}");
        }
    }
}
