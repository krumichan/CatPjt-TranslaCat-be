package jp.co.translacat.global.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtKeyCanonicalizationTest {
    @Test
    void canonicalEncodingPreservesConsumedKeyAndExistingTokensWithoutDeletingPrefix() {
        // 준비: 운영 비밀 대신 선행 padding을 가진 합성 입력으로 두 decoder의 차이를 재현한다.
        byte[] original = new byte[32];
        Arrays.fill(original, (byte) 73);
        String noncanonical = "=" + Base64.getEncoder().encodeToString(original);
        byte[] consumed = Decoders.BASE64.decode(noncanonical);
        String canonical = Base64.getEncoder().encodeToString(consumed);
        var oldService = new JWTService(noncanonical, 60000, 120000);
        var canonicalService = new JWTService(canonical, 60000, 120000);

        // 실행: 기존/정규화 표현 각각으로 서명한 토큰을 상대 표현의 실제 JWTService가 검증한다.
        String oldToken = oldService.generateAccessToken(73L, "fixture@example.invalid");
        String newToken = canonicalService.generateAccessToken(73L, "fixture@example.invalid");

        // 검증: 문자열 정리가 아니라 실제 소비 key material의 재인코딩만 기존 세션을 보존한다.
        assertThatThrownBy(() -> Base64.getDecoder().decode(noncanonical)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Base64.getDecoder().decode(canonical)).isEqualTo(consumed);
        assertThat(Decoders.BASE64.decode(noncanonical.substring(1))).isNotEqualTo(consumed);
        assertThat(canonicalService.getId(oldToken)).isEqualTo(73L);
        assertThat(oldService.getId(newToken)).isEqualTo(73L);
        assertThat(Jwts.parser().verifyWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(canonical)))
                .build().parseSignedClaims(oldToken).getHeader().getAlgorithm()).isEqualTo("HS256");
    }
}
