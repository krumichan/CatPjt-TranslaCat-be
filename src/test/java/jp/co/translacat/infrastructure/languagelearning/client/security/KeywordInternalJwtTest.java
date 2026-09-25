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

import static org.junit.jupiter.api.Assertions.*;

class KeywordInternalJwtTest {
    private static final Instant NOW = Instant.parse("2026-09-24T01:00:00Z");
    private static final byte[] KEY = new byte[32];

    private LanguageLearningInternalJwtProvider provider() {
        var properties = new LanguageLearningClientProperties();
        properties.getInternalJwt().setSecretBase64(Encoders.BASE64.encode(KEY));
        return new LanguageLearningInternalJwtProvider(properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Claims claims(String token) {
        // 발급기와 검증기의 시간을 모두 고정한다. 실제 날짜가 바뀌어도 테스트가 만료되지 않는다.
        return Jwts.parser().clock(() -> Date.from(NOW)).verifyWith(Keys.hmacShaKeyFor(KEY))
                .requireIssuer("translacat-be").requireAudience("translacat-ll")
                .build().parseSignedClaims(token).getPayload();
    }

    @Test
    void userTokenCarriesOnlyVerifiedUserAndMandatoryLearningFact() {
        Claims started = claims(provider().issueKeywordUserToken(123L, true));
        assertEquals("123", started.getSubject());
        assertEquals("ll-keywords", started.get("tokenUse"));
        assertEquals("translacat-be", started.get("service"));
        assertEquals(List.of("USER"), started.get("roles", List.class));
        assertEquals(Boolean.TRUE, started.get("keywordLearningStarted", Boolean.class));
        assertEquals(NOW, started.getIssuedAt().toInstant());
        assertEquals(NOW.plusSeconds(120), started.getExpiration().toInstant());
        assertEquals(Boolean.FALSE,
                claims(provider().issueKeywordUserToken(123L, false)).get("keywordLearningStarted", Boolean.class));
    }

    @Test
    void administratorTokenUsesActualActorAndDistinctKeywordPurpose() {
        Claims value = claims(provider().issueKeywordAdminToken(900L));
        assertEquals("900", value.getSubject());
        assertEquals(List.of("ADMIN"), value.get("roles", List.class));
        assertEquals("ll-keywords", value.get("tokenUse"));
        assertNull(value.get("scopes"));
    }

    @Test
    void originalSettingsTokenPurposesAreNotChanged() {
        assertEquals("ll-internal", claims(provider().issueUserToken(123L)).get("tokenUse"));
        assertEquals("ll-settings-service", claims(provider().issueSettingsServiceToken()).get("tokenUse"));
        assertNull(claims(provider().issueUserToken(123L)).get("keywordLearningStarted"));
    }

    @Test
    void invalidSubjectsAreRejectedBeforeSigning() {
        assertThrows(IllegalArgumentException.class, () -> provider().issueKeywordUserToken(0L, false));
        assertThrows(IllegalArgumentException.class, () -> provider().issueKeywordUserToken(null, true));
        assertThrows(IllegalArgumentException.class, () -> provider().issueKeywordAdminToken(-1L));
    }
}
