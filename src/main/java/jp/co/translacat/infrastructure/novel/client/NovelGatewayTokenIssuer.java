package jp.co.translacat.infrastructure.novel.client;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public final class NovelGatewayTokenIssuer {
    private final SecretKey key;
    private final Clock clock;

    public NovelGatewayTokenIssuer(String secretBase64, Clock clock) {
        this.clock = clock;
        try {
            byte[] bytes = Base64.getDecoder().decode(secretBase64);
            if (bytes.length < 32 || bytes.length > 128) throw new IllegalArgumentException();
            key = Keys.hmacShaKeyFor(bytes);
        } catch (Exception ignored) {
            throw new IllegalStateException("Novel dedicated internal key is missing or invalid.");
        }
    }

    public String issue(long actorId) {
        if (actorId < 1) throw new NovelGatewayException(404, "NOVEL_NOT_FOUND", false);
        // 사용자 JWT/LL/CHAT 키를 재사용하지 않고, 검증한 관리자 주체만 짧게 위임한다.
        var now = clock.instant();
        return Jwts.builder().issuer("translacat-be").audience().add("translacat-novel").and()
                .subject(Long.toString(actorId)).claim("service", "translacat-be")
                .claim("tokenUse", "novel-internal").claim("roles", List.of("ADMIN"))
                .id(UUID.randomUUID().toString()).issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60)))
                .signWith(key, Jwts.SIG.HS256).compact();
    }
}
