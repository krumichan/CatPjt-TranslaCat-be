package jp.co.translacat.infrastructure.chat.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChatCoreServiceTokenTest {
    private static final byte[] KEY = new byte[64];
    private static final Instant NOW = Instant.parse("2026-09-27T00:00:00Z");

    @ParameterizedTest
    @ValueSource(strings = {
            "chat:accounts:read",
            "chat:relations:read",
            "chat:storage:read",
            "chat:storage:write",
            "chat:storage:delete"
    })
    void exactWorkerScopeIsSeparatedFromUserIdentity(String scope) {
        // 준비
        var verifier = verifier();
        var token = sign(claims(scope));

        // 실행
        var principal = verifier.verifyService(token, scope);

        // 검증
        assertThat(principal).isEqualTo(new ChatCoreServicePrincipal("translacat-chat", scope));
        assertThat(verifier.verify(token)).isNull();
        assertThat(verifier.verifyService(token, "chat:identity:read")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "user-sub",
            "other-service",
            "roles",
            "identity-use",
            "wrong-scope",
            "extra-scope",
            "empty-scope",
            "wrong-env",
            "expired",
            "future",
            "long-ttl",
            "string-iat"
    })
    void malformedOrOverprivilegedServiceClaimsFailClosed(String change) {
        // 준비
        var payload = claims("chat:accounts:read");
        switch (change) {
            case "user-sub" -> payload.put("sub", "73");
            case "other-service" -> payload.put("service", "other");
            case "roles" -> payload.put("roles", List.of("ADMIN"));
            case "identity-use" -> payload.put("tokenUse", "chat-identity");
            case "wrong-scope" -> payload.put("scopes", List.of("chat:storage:delete"));
            case "extra-scope" -> payload.put("scopes", List.of("chat:accounts:read", "chat:storage:delete"));
            case "empty-scope" -> payload.put("scopes", List.of());
            case "wrong-env" -> payload.put("environment", "Production");
            case "expired" -> {
                payload.put("iat", NOW.getEpochSecond() - 60);
                payload.put("exp", NOW.getEpochSecond() - 5);
            }
            case "future" -> payload.put("iat", NOW.getEpochSecond() + 6);
            case "long-ttl" -> payload.put("exp", NOW.getEpochSecond() + 121);
            case "string-iat" -> payload.put("iat", Long.toString(NOW.getEpochSecond()));
            default -> throw new IllegalArgumentException();
        }

        // 실행 / 검증
        assertThat(verifier().verifyService(sign(payload), "chat:accounts:read")).isNull();
    }

    private static Map<String, Object> claims(String scope) {
        var result = new LinkedHashMap<String, Object>();
        result.put("iss", "translacat-chat");
        result.put("aud", "translacat-be");
        result.put("sub", "translacat-chat");
        result.put("service", "translacat-chat");
        result.put("tokenUse", "chat-core-service");
        result.put("environment", "Development");
        result.put("scopes", List.of(scope));
        result.put("iat", NOW.getEpochSecond());
        result.put("exp", NOW.getEpochSecond() + 60);
        return result;
    }

    private static String sign(Map<String, Object> claims) {
        try {
            // JJWT builder의 NumericDate 정상화를 피하고 시험할 JSON 타입 그대로 서명한다.
            var encoder = Base64.getUrlEncoder().withoutPadding();
            String header = encoder.encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
            String payload = encoder.encodeToString(new ObjectMapper().writeValueAsBytes(claims));
            String content = header + "." + payload;
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(KEY, "HmacSHA256"));
            return content + "." + encoder.encodeToString(mac.doFinal(content.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new IllegalStateException("Synthetic JWT creation failed.", exception);
        }
    }

    private static ChatCoreIdentityTokenVerifier verifier() {
        var options = new ChatCoreIdentityProperties();
        options.setEnvironment("Development");
        options.setSecretBase64(Base64.getEncoder().encodeToString(KEY));
        return new ChatCoreIdentityTokenVerifier(options, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
