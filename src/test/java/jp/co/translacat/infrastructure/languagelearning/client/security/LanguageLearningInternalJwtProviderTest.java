package jp.co.translacat.infrastructure.languagelearning.client.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import jp.co.translacat.infrastructure.languagelearning.client.config.LanguageLearningClientProperties;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LanguageLearningInternalJwtProviderTest {

    private static final byte[] KEY = new byte[32];
    private static final Instant NOW = Instant.parse("2026-09-24T07:00:00Z");

    @Test
    void userTokenMatchesKtorInternalContract() {
        LanguageLearningClientProperties properties = properties();
        LanguageLearningInternalJwtProvider provider =
                new LanguageLearningInternalJwtProvider(
                        properties,
                        Clock.fixed(NOW, ZoneOffset.UTC)
                );

        String token = provider.issueUserToken(123L);
        Claims claims = Jwts.parser()
                .clock(() -> Date.from(NOW))
                .verifyWith(Keys.hmacShaKeyFor(KEY))
                .requireIssuer("translacat-be")
                .requireAudience("translacat-ll")
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertEquals("123", claims.getSubject());
        assertEquals("translacat-be", claims.get("service", String.class));
        assertEquals("ll-internal", claims.get("tokenUse", String.class));
        assertEquals(List.of("USER"), claims.get("roles", List.class));
        assertEquals(NOW, claims.getIssuedAt().toInstant());
        assertEquals(NOW.plusSeconds(120), claims.getExpiration().toInstant());
    }

    @Test
    void adminTokenUsesAdminRole() {
        LanguageLearningInternalJwtProvider provider =
                new LanguageLearningInternalJwtProvider(
                        properties(),
                        Clock.fixed(NOW, ZoneOffset.UTC)
                );

        Claims claims = Jwts.parser()
                .clock(() -> Date.from(NOW))
                .verifyWith(Keys.hmacShaKeyFor(KEY))
                .build()
                .parseSignedClaims(provider.issueAdminToken(900L))
                .getPayload();

        assertEquals(List.of("ADMIN"), claims.get("roles", List.class));
    }

    @Test
    void invalidKeyAndTtlAreRejected() {
        LanguageLearningClientProperties invalidKey = properties();
        invalidKey.getInternalJwt().setSecretBase64("not-base64");
        assertThrows(
                IllegalArgumentException.class,
                () -> new LanguageLearningInternalJwtProvider(
                        invalidKey,
                        Clock.systemUTC()
                )
        );

        LanguageLearningClientProperties invalidTtl = properties();
        invalidTtl.getInternalJwt().setTtlSeconds(301);
        assertThrows(
                IllegalArgumentException.class,
                () -> new LanguageLearningInternalJwtProvider(
                        invalidTtl,
                        Clock.systemUTC()
                )
        );
    }

    @Test
    void serviceReadTokenHasNoAdminRoleOrFabricatedUserId() {
        var provider = new LanguageLearningInternalJwtProvider(properties(), Clock.fixed(NOW, ZoneOffset.UTC));
        Claims claims = Jwts.parser()
                .clock(() -> java.util.Date.from(NOW))
                .verifyWith(Keys.hmacShaKeyFor(KEY))
                .requireIssuer("translacat-be")
                .requireAudience("translacat-ll")
                .build().parseSignedClaims(provider.issueSettingsServiceToken()).getPayload();
        assertEquals("translacat-be", claims.getSubject());
        assertEquals("ll-settings-service", claims.get("tokenUse", String.class));
        assertEquals(List.of("settings:read"), claims.get("scopes", List.class));
        org.junit.jupiter.api.Assertions.assertNull(claims.get("roles"));
        assertEquals(NOW.plusSeconds(120), claims.getExpiration().toInstant());
    }

    private LanguageLearningClientProperties properties() {
        LanguageLearningClientProperties properties =
                new LanguageLearningClientProperties();
        properties.setUrl("http://localhost:8081");
        properties.getInternalJwt().setSecretBase64(
                Encoders.BASE64.encode(KEY)
        );
        return properties;
    }
}
