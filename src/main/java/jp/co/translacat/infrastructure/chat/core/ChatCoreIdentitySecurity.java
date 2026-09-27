package jp.co.translacat.infrastructure.chat.core;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ChatCoreIdentityProperties.class)
public class ChatCoreIdentitySecurity {
    @Bean
    ChatCoreIdentityTokenVerifier chatCoreIdentityTokenVerifier(ChatCoreIdentityProperties properties) {
        return new ChatCoreIdentityTokenVerifier(properties, Clock.systemUTC());
    }

    @Bean
    @Order(1)
    SecurityFilterChain chatCoreIdentitySecurityFilterChain(HttpSecurity http, ChatCoreIdentityTokenVerifier verifier)
            throws Exception {
        // 전용 내부 namespace만 앞에서 처리한다. 기존 사용자·LL 관리자/오류 경로는 기존 chain이 계속 소유한다.
        return http.securityMatcher("/internal/v1/chat/**")
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, "/internal/v1/chat/identity").hasAuthority("chat:identity:read")
                        .requestMatchers(HttpMethod.POST, "/internal/v1/chat/accounts/lookup").hasAuthority("chat:accounts:read")
                        .requestMatchers(HttpMethod.POST, "/internal/v1/chat/relations/query").hasAuthority("chat:relations:read")
                        .requestMatchers(HttpMethod.POST, "/internal/v1/chat/storage/urls").hasAuthority("chat:storage:read")
                        .requestMatchers(HttpMethod.PUT, "/internal/v1/chat/storage/objects").hasAuthority("chat:storage:write")
                        .requestMatchers(HttpMethod.DELETE, "/internal/v1/chat/storage/objects").hasAuthority("chat:storage:delete")
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) -> response.setStatus(401))
                        .accessDeniedHandler((request, response, error) -> response.setStatus(403)))
                .addFilterBefore(new ChatCoreIdentityFilter(verifier), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
