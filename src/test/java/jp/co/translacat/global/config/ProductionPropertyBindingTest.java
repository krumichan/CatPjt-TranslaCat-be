package jp.co.translacat.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jp.co.translacat.infrastructure.chat.core.ChatCoreIdentityProperties;
import jp.co.translacat.infrastructure.chat.core.ChatCoreIdentityTokenVerifier;
import jp.co.translacat.infrastructure.chat.gateway.ChatGatewayProperties;
import jp.co.translacat.infrastructure.chat.gateway.ChatGatewayTarget;
import jp.co.translacat.infrastructure.chat.gateway.ChatGatewayTokenIssuer;
import jp.co.translacat.infrastructure.languagelearning.client.config.LanguageLearningClientConfiguration;
import jp.co.translacat.infrastructure.languagelearning.client.config.LanguageLearningClientProperties;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import jp.co.translacat.infrastructure.novel.client.NovelGatewayProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionPropertyBindingTest {
    @Test
    void productionNovelUsesEmbeddedReaderAndDedicatedAiKeyWithoutAvailabilitySwitches() throws Exception {
        // 준비: 실제 base/prod에 합성 전용키를 공급한다. 독립 NOVEL 목적지/DB는 없다.
        var environment = environment(values());
        var properties = Binder.get(environment).bindOrCreate("novel.gateway", NovelGatewayProperties.class);

        // 검증: 가용성 토글 없이 같은 AI origin의 전용 인증키를 사용한다.
        assertThat(environment.getProperty("novel.gateway.enabled")).isNull();
        assertThat(environment.getProperty("novel.ai.enabled")).isNull();
        assertThat(properties.getBaseUrl()).isNull();
        assertThat(properties.getSecretBase64()).isNull();
        assertThat(environment.getProperty("novel.ai.base-url")).isEqualTo("https://ai.fixture.invalid:8443");
        assertThat(environment.getProperty("novel.ai.api-key")).isEqualTo("novel-only-synthetic-key");
        var novelValues = values();
        novelValues.remove("NOVEL_AI_SERVER_API_KEY");
        assertThatThrownBy(() -> environment(novelValues).getRequiredProperty("novel.ai.api-key"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("NOVEL_AI_SERVER_API_KEY");
    }

    @Test
    void actualProductionResourceBindsDirectionsAndPreservesCoreTls() throws Exception {
        // 준비: 실제 prod 파일을 읽되 process ENV와 시스템 속성은 제외한다. DB/API는 생성하지 않는다.
        var environment = environment(values());
        var binder = Binder.get(environment);

        // 실행
        var ll = binder.bind("language-learning", LanguageLearningClientProperties.class).get();
        var chat = binder.bind("chat.gateway", ChatGatewayProperties.class).get();
        var core = binder.bind("chat.core.identity", ChatCoreIdentityProperties.class).get();
        var llJwt = new LanguageLearningInternalJwtProvider(ll, Clock.systemUTC());
        var ingress = new ChatGatewayTokenIssuer(chat, Clock.systemUTC());
        var identity = new ChatCoreIdentityTokenVerifier(core, Clock.systemUTC());

        // 검증: 각 방향은 독립 키와 정확한 발급자/대상/용도를 사용한다.
        assertThat(environment.getProperty("language-learning.remote.enabled")).isNull();
        assertThat(environment.getProperty("language-learning.growth.enabled")).isNull();
        assertThat(ll.getUrl()).isEqualTo("https://ll.fixture.invalid:8443");
        assertThat(ll.getInternalJwt().getTtlSeconds()).isEqualTo(120);
        var llClaims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(key(1))).requireIssuer("translacat-be")
                .requireAudience("translacat-ll").build().parseSignedClaims(llJwt.issueUserToken(73L));
        assertThat(llClaims.getHeader().getAlgorithm()).isEqualTo("HS256");
        assertThat(llClaims.getPayload().get("service")).isEqualTo("translacat-be");
        assertThat(llClaims.getPayload().get("tokenUse")).isEqualTo("ll-internal");
        assertThat(environment.getProperty("chat.gateway.enabled")).isNull();
        assertThat(environment.getProperty("chat.core.identity.enabled")).isNull();
        assertThat(chat.getEnvironment()).isEqualTo("Production");
        assertThat(core.getEnvironment()).isEqualTo("Production");
        assertThat(new ChatGatewayTarget(chat).webSocketUri().toString())
                .isEqualTo("wss://chat.fixture.invalid:8443/ws/chat");
        String ingressToken = ingress.issue(73L, "chat:http");
        var chatClaims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(key(2))).requireIssuer("translacat-be")
                .requireAudience("translacat-chat").build().parseSignedClaims(ingressToken);
        assertThat(chatClaims.getHeader().getAlgorithm()).isEqualTo("HS256");
        assertThat(chatClaims.getPayload().get("tokenUse")).isEqualTo("chat-ingress");
        assertThat(chatClaims.getPayload().get("environment")).isEqualTo("Production");
        assertThat(chatClaims.getPayload().get("scopes")).isEqualTo(List.of("chat:http"));
        assertThat(identity.verify(identityToken("Production", key(3)))).isNotNull();
        assertThat(identity.verify(identityToken("Development", key(3)))).isNull();
        assertThat(identity.verify(identityToken("Production", key(2)))).isNull();
        assertThat(identity.verify(ingressToken)).isNull();
        assertThat(identity.verify(llJwt.issueUserToken(73L))).isNull();

        // 검증: JDBC TLS를 유지하며 운영 자동 DDL과 로컬 profile 혼입을 막는다.
        assertThat(environment.getRequiredProperty("spring.datasource.url"))
                .isEqualTo("jdbc:log4jdbc:mysql://db.fixture.invalid:4000/translacat"
                        + "?rewriteBatchedStatements=true&sslMode=VERIFY_IDENTITY&enabledTLSProtocols=TLSv1.2,TLSv1.3");
        assertThat(environment.getRequiredProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getRequiredProperty("ai-server.url")).isEqualTo("https://ai.fixture.invalid:8443");
        assertThat(environment.getRequiredProperty("cors.allowed-origin")).isEqualTo("https://fe.fixture.invalid");
        assertThat(environment.getRequiredProperty("translacat.storage.s3.region")).isEqualTo("auto");
    }

    @ParameterizedTest
    @ValueSource(strings = {"DB_URL", "DB_USERNAME", "DB_PASSWORD", "GOOGLE_CLIENT_ID", "FRONTEND_URL",
            "GEMINI_API_KEY", "GOOGLE_PROXY_URL", "AI_SERVER_URL", "AI_SERVER_API_KEY", "JWT_SECRET_KEY",
            "CLOUDFLARE_R2_BUCKET", "CLOUDFLARE_R2_ACCESS_KEY_ID", "CLOUDFLARE_R2_SECRET_ACCESS_KEY",
            "CLOUDFLARE_R2_ENDPOINT", "CLOUDFLARE_R2_REGION", "CLOUDFLARE_R2_PUBLIC_BASE_URL",
            "LL_INTERNAL_SERVER_URL", "LL_INTERNAL_JWT_SECRET_BASE64", "CHAT_GATEWAY_SECRET_BASE64",
            "CHAT_CORE_IDENTITY_SECRET_BASE64"})
    void requiredProductionPlaceholderDoesNotSilentlyResolveFromHost(String missing) throws Exception {
        // 준비: 합성 값 하나만 제거하며 실제 호스트 비밀을 fallback으로 사용하지 않는다.
        var values = values();
        values.remove(missing);
        var environment = environment(values);
        var sources = new PropertiesPropertySourceLoader().load("prod-check", new ClassPathResource("application-prod.properties"));

        // 실행 / 검증: Spring의 실제 placeholder 해석이 누락을 실패로 처리한다.
        var names = ((org.springframework.core.env.EnumerablePropertySource<?>) sources.getFirst()).getPropertyNames();
        assertThatThrownBy(() -> {
            for (String name : names) environment.getRequiredProperty(name);
        }).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(missing);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "%%%", "c2hvcnQ="})
    void boundDirectionalKeysRejectEmptyMalformedAndShortMaterial(String invalid) throws Exception {
        // 준비
        var values = values();
        values.put("LL_INTERNAL_JWT_SECRET_BASE64", invalid);
        values.put("CHAT_GATEWAY_SECRET_BASE64", invalid);
        values.put("CHAT_CORE_IDENTITY_SECRET_BASE64", invalid);
        var binder = Binder.get(environment(values));

        // 실행 / 검증: 서비스가 항상 등록돼도 잘못된 인증키로 시작할 수 없다.
        var ll = binder.bind("language-learning", LanguageLearningClientProperties.class).get();
        var chat = binder.bind("chat.gateway", ChatGatewayProperties.class).get();
        var core = binder.bind("chat.core.identity", ChatCoreIdentityProperties.class).get();
        assertThatThrownBy(() -> new LanguageLearningInternalJwtProvider(ll, Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChatGatewayTokenIssuer(chat, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ChatCoreIdentityTokenVerifier(core, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void productionChatAndRemoteLanguageLearningRejectCleartextPublicOrigins() throws Exception {
        // 준비
        var values = values();
        values.put("CHAT_GATEWAY_BASE_URL", "http://chat.fixture.invalid:5085");
        values.put("LL_INTERNAL_SERVER_URL", "http://ll.fixture.invalid:8081");
        var binder = Binder.get(environment(values));
        var chat = binder.bind("chat.gateway", ChatGatewayProperties.class).get();
        var ll = binder.bind("language-learning", LanguageLearningClientProperties.class).get();

        // 실행 / 검증: 클라이언트 생성 시점에 거부하므로 외부 통신이 발생하지 않는다.
        assertThatThrownBy(() -> new ChatGatewayTarget(chat)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new LanguageLearningClientConfiguration().languageLearningRestClient(RestClient.builder(), ll))
                .isInstanceOf(IllegalStateException.class);
    }

    private static StandardEnvironment environment(Map<String, Object> values) throws Exception {
        var environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("synthetic-env", values));
        var loader = new PropertiesPropertySourceLoader();
        loader.load("prod", new ClassPathResource("application-prod.properties"))
                .forEach(environment.getPropertySources()::addLast);
        loader.load("base", new ClassPathResource("application.properties"))
                .forEach(environment.getPropertySources()::addLast);
        return environment;
    }

    private static Map<String, Object> values() {
        var values = new LinkedHashMap<String, Object>();
        for (String key : List.of("DB_USERNAME", "DB_PASSWORD", "GOOGLE_CLIENT_ID", "GEMINI_API_KEY",
                "GOOGLE_PROXY_URL", "AI_SERVER_API_KEY", "CLOUDFLARE_R2_BUCKET", "CLOUDFLARE_R2_ACCESS_KEY_ID",
                "CLOUDFLARE_R2_SECRET_ACCESS_KEY")) values.put(key, "synthetic-only");
        values.put("DB_URL", "jdbc:log4jdbc:mysql://db.fixture.invalid:4000/translacat");
        values.put("FRONTEND_URL", "https://fe.fixture.invalid");
        values.put("AI_SERVER_URL", "https://ai.fixture.invalid:8443");
        values.put("LL_INTERNAL_SERVER_URL", "https://ll.fixture.invalid:8443");
        values.put("CLOUDFLARE_R2_ENDPOINT", "https://r2.fixture.invalid");
        values.put("CLOUDFLARE_R2_PUBLIC_BASE_URL", "https://assets.fixture.invalid");
        values.put("CLOUDFLARE_R2_REGION", "auto");
        values.put("JWT_SECRET_KEY", Base64.getEncoder().encodeToString(key(4)));
        values.put("LL_INTERNAL_JWT_SECRET_BASE64", Base64.getEncoder().encodeToString(key(1)));
        values.put("CHAT_GATEWAY_SECRET_BASE64", Base64.getEncoder().encodeToString(key(2)));
        values.put("CHAT_CORE_IDENTITY_SECRET_BASE64", Base64.getEncoder().encodeToString(key(3)));
        values.put("NOVEL_AI_SERVER_API_KEY", "novel-only-synthetic-key");
        values.put("CHAT_GATEWAY_BASE_URL", "https://chat.fixture.invalid:8443");
        values.put("CHAT_RUNTIME_ENVIRONMENT", "Production");
        return values;
    }

    private static byte[] key(int value) {
        byte[] key = new byte[64];
        Arrays.fill(key, (byte) value);
        return key;
    }

    private static String identityToken(String environment, byte[] key) {
        var now = java.time.Instant.now();
        return Jwts.builder().issuer("translacat-chat").audience().add("translacat-be").and()
                .subject("73").claim("service", "translacat-chat").claim("tokenUse", "chat-identity")
                .claim("environment", environment).claim("scopes", List.of("chat:identity:read"))
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(key), Jwts.SIG.HS256).compact();
    }
}
