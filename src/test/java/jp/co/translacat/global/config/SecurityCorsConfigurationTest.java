package jp.co.translacat.global.config;

import jp.co.translacat.global.logging.ApiLoggingFilter;
import jp.co.translacat.global.security.JwtFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityCorsConfigurationTest {
    private static final String FRONTEND = "https://fe.fixture.invalid";
    private static final String NATIVE = "https://native.fixture.invalid";

    @Test
    void unsetOverridePreservesTheExistingFrontendAndRejectsNativeOrigin() throws Exception {
        // 준비: 실제 prod properties를 읽되 호스트 환경과 외부 서비스는 사용하지 않는다.
        CorsConfigurationSource source = productionSource(null);

        // 실행 / 검증: 선택 설정이 없으면 기존 웹만 통과한다.
        assertAccepted(source, FRONTEND);
        assertRejected(source, NATIVE);
    }

    @ParameterizedTest
    @ValueSource(strings = {FRONTEND, NATIVE})
    void explicitTwoOriginsPassTheActualSecurityCorsFilter(String origin) throws Exception {
        // 준비: 웹 origin을 보존한 두 정확한 origin을 실제 SecurityConfig에 공급한다.
        CorsConfigurationSource source = productionSource(FRONTEND + "," + NATIVE);

        // 실행 / 검증: 공유 CORS source를 사용하는 WS handshake와 HTTP 모두 허용된다.
        assertAccepted(source, origin);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://unregistered.fixture.invalid", "https://native.fixture.invalid.evil",
            "http://native.fixture.invalid", "https://native.fixture.invalid:8443", "null"})
    void unregisteredOriginsCannotReachTheDownstreamChain(String origin) throws Exception {
        // 준비
        CorsConfigurationSource source = productionSource(FRONTEND + "," + NATIVE);

        // 실행 / 검증: wildcard나 다른 port/scheme으로 허용 범위를 넓히지 않는다.
        assertRejected(source, origin);
    }

    @Test
    void canonicalNativeIpv4OriginIsAllowedButADifferentPortIsRejected() throws Exception {
        // 준비: 실제 기기와 같은 IP origin 형태를 합성 문서용 주소로 검사한다.
        String nativeIp = "https://198.51.100.20";
        CorsConfigurationSource source = productionSource(FRONTEND + "," + nativeIp);

        // 실행 / 검증: 정확한 origin만 통과하며 다른 port에는 허용 헤더를 주지 않는다.
        assertAccepted(source, nativeIp);
        assertRejected(source, nativeIp + ":8443");
    }

    @Test
    void unregisteredPreflightReturns403WithoutCorsHeadersOrDownstreamExecution() throws Exception {
        // 준비: 미등록 브라우저의 preflight를 실제 보안 CORS source에 전달한다.
        CorsConfigurationSource source = productionSource(FRONTEND + "," + NATIVE);
        MockHttpServletRequest request = request("OPTIONS", "/api/v1/chat/rooms", "https://unregistered.fixture.invalid");
        request.addHeader("Access-Control-Request-Method", "GET");
        request.addHeader("Access-Control-Request-Headers", "Authorization");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chained = new AtomicBoolean();

        // 실행
        new CorsFilter(source).doFilter(request, response, (ignoredRequest, ignoredResponse) -> chained.set(true));

        // 검증: 차단한 origin을 반사하지 않고 실제 endpoint까지 진행하지 않는다.
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
        assertThat(chained).isFalse();
    }

    @Test
    void permittedPreflightKeepsCredentialAndAuthorizationHeaderSupport() throws Exception {
        // 준비: 실제 source를 사용하는 OPTIONS 요청은 HTTP API의 기존 헤더 계약을 유지한다.
        CorsConfigurationSource source = productionSource(FRONTEND + "," + NATIVE);
        MockHttpServletRequest request = request("OPTIONS", "/api/v1/chat/rooms", NATIVE);
        request.addHeader("Access-Control-Request-Method", "GET");
        request.addHeader("Access-Control-Request-Headers", "Authorization");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chained = new AtomicBoolean();

        // 실행
        new CorsFilter(source).doFilter(request, response, (ignoredRequest, ignoredResponse) -> chained.set(true));

        // 검증: preflight는 filter에서 끝나고 자격 허용은 요청 origin에만 반사한다.
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo(NATIVE);
        assertThat(response.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(response.getHeader("Access-Control-Allow-Headers")).contains("Authorization");
        assertThat(chained).isFalse();
    }

    @Test
    void requestsWithoutOriginKeepExistingNonBrowserBehavior() throws Exception {
        // 준비
        CorsConfigurationSource source = productionSource(FRONTEND + "," + NATIVE);
        MockHttpServletRequest request = request("GET", "/ws/chat", null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chained = new AtomicBoolean();

        // 실행
        new CorsFilter(source).doFilter(request, response, (ignoredRequest, ignoredResponse) -> chained.set(true));

        // 검증: Origin 없는 연결을 새 override가 차단하지 않는다. STOMP 인증은 별도 계약이다.
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
        assertThat(chained).isTrue();
    }

    private static CorsConfigurationSource productionSource(String override) throws Exception {
        // 준비: 시스템 환경을 제거해 실제 비밀값 없이 prod fallback/override placeholder를 해석한다.
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        Map<String, Object> values = new LinkedHashMap<>(Map.of("FRONTEND_URL", FRONTEND));
        if (override != null) values.put("CORS_ALLOWED_ORIGINS", override);
        environment.getPropertySources().addFirst(new MapPropertySource("synthetic-env", values));
        new PropertiesPropertySourceLoader().load("prod", new ClassPathResource("application-prod.properties"))
                .forEach(environment.getPropertySources()::addLast);

        // 실행: 보안 filter는 대체하지 않고 실제 SecurityConfig가 만든 CORS source를 사용한다.
        SecurityConfig security = new SecurityConfig(mock(JwtFilter.class), mock(ApiLoggingFilter.class));
        ReflectionTestUtils.setField(security, "allowedOrigin",
                Arrays.asList(environment.getRequiredProperty("cors.allowed-origin").split(",")));
        return security.corsConfigurationSource();
    }

    private static MockHttpServletRequest request(String method, String path, String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setScheme("https");
        request.setSecure(true);
        request.setServerName("be.fixture.invalid");
        request.setServerPort(443);
        if (origin != null) request.addHeader("Origin", origin);
        return request;
    }

    private static void assertAccepted(CorsConfigurationSource source, String origin) throws Exception {
        // 준비: WS handshake와 HTTP API에 동일한 origin 정책을 적용한다.
        for (String path : new String[]{"/ws/chat", "/api/v1/chat/rooms"}) {
            MockHttpServletRequest request = request("GET", path, origin);
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean chained = new AtomicBoolean();

            // 실행
            new CorsFilter(source).doFilter(request, response, (ignoredRequest, ignoredResponse) -> chained.set(true));

            // 검증
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo(origin);
            assertThat(response.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
            assertThat(chained).isTrue();
        }
    }

    private static void assertRejected(CorsConfigurationSource source, String origin) throws Exception {
        // 준비
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chained = new AtomicBoolean();

        // 실행
        new CorsFilter(source).doFilter(request("GET", "/ws/chat", origin), response,
                (ignoredRequest, ignoredResponse) -> chained.set(true));

        // 검증
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
        assertThat(chained).isFalse();
    }
}
