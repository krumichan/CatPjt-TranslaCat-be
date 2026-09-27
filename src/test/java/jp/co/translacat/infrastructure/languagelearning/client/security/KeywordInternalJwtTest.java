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
    void userTokenCarriesVerifiedIdentityWithoutCoreLearningState() {
        // 준비·실행: LL이 학습 사실을 조회하므로 BE는 인증 주체만 서명한다.
        Claims started = claims(provider().issueKeywordUserToken(123L));

        // 검증
        assertEquals("123", started.getSubject());
        assertEquals("ll-keywords", started.get("tokenUse"));
        assertEquals("translacat-be", started.get("service"));
        assertEquals(List.of("USER"), started.get("roles", List.class));
        assertNull(started.get("keywordLearningStarted"));
        assertEquals(NOW, started.getIssuedAt().toInstant());
        assertEquals(NOW.plusSeconds(120), started.getExpiration().toInstant());
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
        assertThrows(IllegalArgumentException.class, () -> provider().issueKeywordUserToken(0L));
        assertThrows(IllegalArgumentException.class, () -> provider().issueKeywordUserToken(null));
        assertThrows(IllegalArgumentException.class, () -> provider().issueKeywordAdminToken(-1L));
    }
}
