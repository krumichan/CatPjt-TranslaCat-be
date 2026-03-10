package jp.co.translacat.global.config;

import jp.co.translacat.global.logging.ApiLoggingFilter;
import jp.co.translacat.global.security.JwtFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 설정 클래스.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Value("${cors.allowed-origin}")
    private List<String> allowedOrigin;

    /**
     * JWT 인증/인가 필터
     * - 모든 요청 전에 JWT 토큰을 검증
     */
    private final JwtFilter jwtFilter;

    private final ApiLoggingFilter loggingFilter;

    /**
     * SecurityFilterChain Bean 등록
     * - CSRF 비활성화
     * - swagger, user register, login 경로는 인증 없이 허용
     * - 나머지 경로는 인증 필요
     * - 세션을 사용하지 않는 Stateless 설정
     * - JWT 필터를 UsernamePasswordAuthenticationFilter 앞에 적용
     *
     * @param http HttpSecurity 객체
     * @param authenticationManager AuthenticationManager Bean
     * @return SecurityFilterChain 구성 객체
     * @throws Exception Security 설정 중 예외 발생 가능
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {

        return http
                .cors(corsConfigurer -> corsConfigurer.configurationSource(this.corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable) // CSRF 비활성화 (REST API 사용 시 필요)
                .authenticationManager(authenticationManager) // AuthenticationManager 설정
                .authorizeHttpRequests(request -> request // URL별 접근 권한 설정
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/token/refresh",
                                "/api/v1/auth/social/**",
                                "/api/v1/health",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-resources/**",
                                "/swagger-ui.html"
                                ).permitAll()  // 위 경로는 인증 없이 접근 허용
                        .anyRequest().authenticated())  // 나머지 경로는 인증 필요
//                .formLogin(Customizer.withDefaults()) // login default form 사용.
                .httpBasic(Customizer.withDefaults()) // HTTP Basic 인증 사용
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 세션을 사용하지 않는 Stateless 정책 설정
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class) // JWT 필터를 UsernamePasswordAuthenticationFilter 앞에 적용
                .addFilterAfter(loggingFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

//  // Spring Security의 유저 정보를 하드코딩으로 작성할 때 사용.
//    @Bean
//    public UserDetailsService userDetailsService() {
//
//        UserDetails user1 = User
//                .withDefaultPasswordEncoder()
//                .username("default_user1")
//                .password("default_password1")
//                .roles("USER")
//                .build();
//
//        UserDetails user2 = User
//                .withDefaultPasswordEncoder()
//                .username("default_user2")
//                .password("default_password2")
//                .roles("ADMIN")
//                .build();
//
//        return new InMemoryUserDetailsManager(user1, user2);
//    }

    /**
     * PasswordEncoder Bean 등록
     * - BCrypt를 사용하여 비밀번호 해싱
     * - 암호화 강도(strength) 12 적용
     *
     * @return BCryptPasswordEncoder 인스턴스
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * AuthenticationManager Bean 등록
     * - AuthenticationConfiguration에서 AuthenticationManager를 가져와 Bean으로 등록
     *
     * @param config AuthenticationConfiguration 객체
     * @return AuthenticationManager 인스턴스
     * @throws Exception
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(allowedOrigin);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "DELETE", "PUT", "HEAD", "OPTION", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
