package jp.co.translacat.infrastructure.chat.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Set;

public final class ChatGatewayTokenIssuer {
    private final ChatGatewayProperties properties;
    private final Clock clock;
    private final SecretKey key;

    public ChatGatewayTokenIssuer(ChatGatewayProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;

        // BE→CHAT 키는 사용자 JWT와 CHAT→BE 키와 별도로 주입한다. 값이나 토큰을 로그에 남기지 않는다.
        try {
            if (blank(properties.getEnvironment()) || blank(properties.getIssuer()) || blank(properties.getAudience())
                    || blank(properties.getService()) || properties.getTokenLifetimeSeconds() < 1
                    || properties.getTokenLifetimeSeconds() > 120) throw new IllegalArgumentException();
            byte[] decoded = Base64.getDecoder().decode(properties.getSecretBase64());
            if (decoded.length < 32 || decoded.length > 128) throw new IllegalArgumentException();
            this.key = Keys.hmacShaKeyFor(decoded);
        } catch (Exception ignored) {
            throw new IllegalStateException("Chat gateway service authentication settings are invalid or missing.");
        }
    }

    public String issue(long userId, String scope) {
        if (userId <= 0 || !Set.of("chat:http", "chat:realtime", "chat:admin").contains(scope)) {
            throw new IllegalArgumentException("Invalid Chat gateway subject or scope.");
        }
        var now = clock.instant();
        return Jwts.builder().issuer(properties.getIssuer()).audience().add(properties.getAudience()).and()
                .subject(Long.toString(userId)).claim("service", properties.getService())
                .claim("tokenUse", "chat-ingress").claim("environment", properties.getEnvironment())
                .claim("scopes", List.of(scope)).issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.getTokenLifetimeSeconds())))
                .signWith(key, Jwts.SIG.HS256).compact();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
