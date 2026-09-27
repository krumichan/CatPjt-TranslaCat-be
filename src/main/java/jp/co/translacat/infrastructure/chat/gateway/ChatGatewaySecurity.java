package jp.co.translacat.infrastructure.chat.gateway;

import jp.co.translacat.global.logging.ApiLoggingFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "chat.gateway", name = "enabled", havingValue = "true")
public class ChatGatewaySecurity {
    @Bean
    @Order(2)
    SecurityFilterChain chatGatewaySecurityFilterChain(HttpSecurity http, ChatGatewayProperties properties,
            ChatGatewayTarget target, ChatGatewayTokenIssuer issuer, ChatGatewayUserAuthenticator authenticator,
            @Qualifier("corsConfigurationSource") CorsConfigurationSource corsConfigurationSource, ApiLoggingFilter logging) throws Exception {
        return http.securityMatcher("/api/v1/chat/**", "/api/v1/admin/chat/**", "/api/v1/users/me/chat-language-settings", "/ws/chat", "/ws/chat/**")
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/ws/chat", "/ws/chat/**").permitAll()
                        .requestMatchers("/api/v1/admin/chat/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) -> response.setStatus(401))
                        .accessDeniedHandler((request, response, error) -> response.setStatus(403)))
                .addFilterBefore(new ChatGatewayAuthenticationFilter(authenticator), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(logging, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new ChatGatewayForwardingFilter(properties, target, issuer), AuthorizationFilter.class)
                .build();
    }
}
