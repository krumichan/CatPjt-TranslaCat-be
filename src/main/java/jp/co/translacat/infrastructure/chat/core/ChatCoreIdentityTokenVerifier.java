package jp.co.translacat.infrastructure.chat.core;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.util.Base64;
import java.util.Date;
import java.util.Set;

public final class ChatCoreIdentityTokenVerifier {
    private final ChatCoreIdentityProperties properties;
    private final Clock clock;
    private final SecretKey key;
    private final ObjectMapper mapper = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    public ChatCoreIdentityTokenVerifier(ChatCoreIdentityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.key = configuredKey(properties);
    }

    public ChatCorePrincipal verify(String token) {
        try {
            JsonNode payload = verifiedPayload(token, "chat-identity", "chat:identity:read");
            if (payload == null) return null;

            JsonNode subject = payload.path("sub");
            if (!subject.isTextual() || !subject.textValue().matches("[1-9][0-9]{0,18}")) return null;
            long userId = Long.parseLong(subject.textValue());
            return userId > 0 ? new ChatCorePrincipal(userId) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public ChatCoreServicePrincipal verifyService(String token, String expectedScope) {
        if (!Set.of("chat:accounts:read", "chat:relations:read", "chat:storage:read", "chat:storage:write",
                        "chat:storage:delete")
                .contains(expectedScope)) return null;
        JsonNode payload = verifiedPayload(token, "chat-core-service", expectedScope);
        if (payload == null || !payload.path("sub").isTextual()
                || !properties.getService().equals(payload.path("sub").textValue()) || payload.has("roles"))
            return null;

        // worker의 서비스 권한은 사용자의 identity/role을 대신하지 않는다.
        return new ChatCoreServicePrincipal(properties.getService(), expectedScope);
    }

    private JsonNode verifiedPayload(String token, String tokenUse, String scope) {
        if (token == null || token.length() > 8192) return null;

        try {
            // 공개 사용자 JWT/LL 토큰과 다른 키·발급자·대상·용도를 검증한다.
            var signed = Jwts.parser().verifyWith(key)
                    .requireIssuer(properties.getIssuer())
                    .requireAudience(properties.getAudience())
                    .require("service", properties.getService())
                    .require("tokenUse", tokenUse)
                    .require("environment", properties.getEnvironment())
                    .clock(() -> Date.from(clock.instant())).clockSkewSeconds(5)
                    .build().parseSignedClaims(token);
            if (!"HS256".equals(signed.getHeader().getAlgorithm())) return null;

            // JJWT가 검증한 뒤 원본 JSON 타입과 중복을 검사한다. 숫자 문자열이나 수명 확장을 허용하지 않는다.
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) return null;
            JsonNode payload = mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode issued = payload.path("iat");
            JsonNode expires = payload.path("exp");
            JsonNode scopes = payload.path("scopes");
            if (!issued.isIntegralNumber() || !issued.canConvertToLong()
                    || !expires.isIntegralNumber() || !expires.canConvertToLong()
                    || !scopes.isArray() || scopes.size() != 1 || !scopes.get(0).isTextual()
                    || !scope.equals(scopes.get(0).textValue())) return null;

            long issuedAt = issued.longValue();
            long expiresAt = expires.longValue();
            long now = clock.instant().getEpochSecond();
            if (expiresAt <= issuedAt || Math.subtractExact(expiresAt, issuedAt) > 120
                    || issuedAt > now + 5 || expiresAt <= now - 5) return null;
            return payload;
        } catch (Exception ignored) {
            // 토큰이나 라이브러리 예외에 들어 있는 claims를 로그/응답으로 전달하지 않는다.
            return null;
        }
    }

    private static SecretKey configuredKey(ChatCoreIdentityProperties properties) {
        if (blank(properties.getEnvironment()) || blank(properties.getIssuer())
                || blank(properties.getAudience()) || blank(properties.getService()) || blank(
                properties.getSecretBase64())) {
            throw new IllegalStateException("Chat Core identity authentication settings are required.");
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(properties.getSecretBase64());
            if (bytes.length < 32 || bytes.length > 128) throw new IllegalArgumentException();
            return Keys.hmacShaKeyFor(bytes);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalStateException(
                    "Chat Core identity signing key must contain 32 to 128 Base64-encoded bytes.");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
