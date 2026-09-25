package jp.co.translacat.infrastructure.languagelearning.resultjournal;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import jp.co.translacat.infrastructure.languagelearning.client.config.LanguageLearningClientProperties;
import jp.co.translacat.infrastructure.languagelearning.client.security.LanguageLearningInternalJwtProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResultJournalJwtTest {
    @Test
    void resultTokenHasOnlyItsOwnPurposeAndScope() {
        byte[] key = new byte[32];
        Instant now = Instant.parse("2026-09-24T03:00:00Z");
        var properties = new LanguageLearningClientProperties();
        properties.getInternalJwt().setSecretBase64(Encoders.BASE64.encode(key));
        var provider = new LanguageLearningInternalJwtProvider(properties, Clock.fixed(now, ZoneOffset.UTC));
        var claims = Jwts.parser()
                .clock(() -> Date.from(now))
                .verifyWith(Keys.hmacShaKeyFor(key))
                .requireIssuer("translacat-be")
                .requireAudience("translacat-ll")
                .build()
                .parseSignedClaims(provider.issueLearningResultsToken())
                .getPayload();
        assertEquals("translacat-be", claims.getSubject());
        assertEquals("ll-learning-results-v1", claims.get("tokenUse", String.class));
        assertEquals(List.of("learning-results:write"), claims.get("scopes", List.class));
        assertNull(claims.get("roles"));
        assertEquals(now.plusSeconds(120), claims.getExpiration().toInstant());
    }
}
