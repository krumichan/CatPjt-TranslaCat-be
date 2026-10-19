package jp.co.translacat.domain.user.auth.mobile;

import jp.co.translacat.domain.user.auth.AuthException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MobileAuthApprovalVerifierTest {
    static final String KEY = "synthetic-mobile-approval-key-for-tests-2026";
    private static final String ID = "11111111-1111-4111-8111-111111111111";
    private static final String TOKEN = "synthetic.access.token";
    private static final String TIMESTAMP = "1800000000";
    private static final String VECTOR = "81e5001286c1b1ba31f5f805ff96b6d17f7fdd7d1ebbc366cbb3e36e9acf25bf";
    private final Clock clock = Clock.fixed(Instant.ofEpochSecond(1800000000), ZoneOffset.UTC);

    @Test
    void fixed_protocol_matches_independently_calculated_hmac_vector() throws Exception {
        // 준비: 공개 합성 벡터는 .NET HMACSHA256으로 독립 계산한 값이다.
        var verifier = new MobileAuthApprovalVerifier(KEY, clock);

        // 실행·검증: newline·경로·Bearer hash·UTF-8 key 계약이 FE와 일치해야 한다.
        assertThat(sign(ID, TOKEN, TIMESTAMP)).isEqualTo(VECTOR);
        assertThatCode(() -> verifier.verify(ID, "Bearer " + TOKEN, TIMESTAMP, VECTOR)).doesNotThrowAnyException();
    }

    @Test
    void stolen_access_alone_or_changed_request_token_or_signature_cannot_authorize() {
        // 준비
        var verifier = new MobileAuthApprovalVerifier(KEY, clock);

        // 실행·검증: 유효한 access 소유만으로 장기 refresh 발급 승인에 도달하지 못한다.
        rejected(() -> verifier.verify(ID, "Bearer " + TOKEN, null, null));
        rejected(() -> verifier.verify("22222222-2222-4222-8222-222222222222", "Bearer " + TOKEN, TIMESTAMP, VECTOR));
        rejected(() -> verifier.verify(ID, "Bearer other.access.token", TIMESTAMP, VECTOR));
        rejected(() -> verifier.verify(ID, "Bearer " + TOKEN, TIMESTAMP, "0".repeat(64)));
        rejected(() -> verifier.verify(ID, "Bearer " + TOKEN, TIMESTAMP, VECTOR.toUpperCase()));
    }

    @Test
    void signed_timestamps_are_bounded_to_sixty_seconds_in_both_directions() throws Exception {
        // 준비
        var verifier = new MobileAuthApprovalVerifier(KEY, clock);

        // 실행·검증: 경계 60초는 허용하고 오래되거나 미래의 서명은 거부한다.
        for (long offset : new long[]{-60, 60}) {
            String timestamp = Long.toString(1800000000 + offset);
            String signature = sign(ID, TOKEN, timestamp);
            assertThatCode(() -> verifier.verify(ID, "Bearer " + TOKEN, timestamp, signature)).doesNotThrowAnyException();
        }
        for (long offset : new long[]{-61, 61}) {
            String timestamp = Long.toString(1800000000 + offset);
            String signature = sign(ID, TOKEN, timestamp);
            rejected(() -> verifier.verify(ID, "Bearer " + TOKEN, timestamp, signature));
        }
        rejected(() -> verifier.verify(ID, "Bearer " + TOKEN, "01800000000", VECTOR));
        rejected(() -> verifier.verify(ID, "Bearer " + TOKEN, "99999999999999999999999999", VECTOR));
    }

    @Test
    void absent_blank_or_short_dedicated_secret_fails_closed_without_affecting_bean_creation() {
        // 실행·검증: 운영 키를 코드에 만들거나 일반 로그인 기동을 중단하지 않는다.
        for (String key : new String[]{null, "", " ".repeat(40), "short-synthetic-key"}) {
            var verifier = new MobileAuthApprovalVerifier(key, clock);
            assertThatThrownBy(() -> verifier.verify(ID, "Bearer " + TOKEN, TIMESTAMP, VECTOR))
                    .isInstanceOfSatisfying(AuthException.class, failure -> {
                        assertThat(failure.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                        assertThat(failure.getErrorCode()).isEqualTo("MOBILE_AUTH_NOT_CONFIGURED");
                        assertThat(failure.getMessage()).doesNotContain(TOKEN, VECTOR);
                    });
        }
    }

    @Test
    void malformed_headers_are_generic_failures_and_unicode_key_uses_utf8_bytes() throws Exception {
        // 준비: raw 문자열을 Base64 decode/trim하지 않는 UTF-8 key 계약도 검증한다.
        var verifier = new MobileAuthApprovalVerifier(KEY, clock);
        String unicodeKey = "합성전용키-".repeat(4);
        var unicode = new MobileAuthApprovalVerifier(unicodeKey, clock);

        // 실행·검증
        rejected(() -> verifier.verify("invalid", "Bearer " + TOKEN, TIMESTAMP, VECTOR));
        rejected(() -> verifier.verify(ID, TOKEN, TIMESTAMP, VECTOR));
        rejected(() -> verifier.verify(ID, "Bearer ", TIMESTAMP, VECTOR));
        rejected(() -> verifier.verify(ID, "Bearer " + TOKEN + " ", TIMESTAMP, VECTOR));
        String unicodeSignature = sign(unicodeKey, ID, TOKEN, TIMESTAMP);
        assertThatCode(() -> unicode.verify(ID, "Bearer " + TOKEN, TIMESTAMP, unicodeSignature)).doesNotThrowAnyException();
    }

    private void rejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(AuthException.class, failure -> {
            assertThat(failure.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(failure.getErrorCode()).isEqualTo("MOBILE_AUTH_APPROVAL_INVALID");
            assertThat(failure.getMessage()).doesNotContain(TOKEN, VECTOR);
        });
    }

    static String sign(String requestId, String token, String timestamp) throws Exception {
        return sign(KEY, requestId, token, timestamp);
    }

    private static String sign(String key, String requestId, String token, String timestamp) throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
        String message = String.join("\n", "mobile-auth-approval-v1", "POST", "/api/v1/auth/mobile/authorize",
                requestId, hash, timestamp);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    }
}
