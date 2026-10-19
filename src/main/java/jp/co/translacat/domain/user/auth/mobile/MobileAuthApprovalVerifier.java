package jp.co.translacat.domain.user.auth.mobile;

import jp.co.translacat.domain.user.auth.AuthException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;

/** 웹 BFF의 명시적 승인과 단순 access 토큰 소유를 별개의 증명으로 확인한다. */
@Component
public class MobileAuthApprovalVerifier {
    public static final String TIMESTAMP_HEADER = "X-Mobile-Approval-Timestamp";
    public static final String SIGNATURE_HEADER = "X-Mobile-Approval-Signature";
    private static final long MAX_SKEW_SECONDS = 60;
    private final byte[] key;
    private final Clock clock;

    @Autowired
    public MobileAuthApprovalVerifier(@Value("${MOBILE_AUTH_APPROVAL_SECRET:}") String secret) {
        this(secret, Clock.systemUTC());
    }

    MobileAuthApprovalVerifier(String secret, Clock clock) {
        byte[] configured = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        this.key = configured.length >= 32 && !secret.isBlank() ? configured : null;
        this.clock = clock;
    }

    public void verify(String requestId, String authorization, String timestamp, String signature) {
        // 전용 키가 없으면 모바일 승인만 닫는다. access 탈취만으로 refresh 세션을 만들 수 없어야 한다.
        if (key == null) {
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "MOBILE_AUTH_NOT_CONFIGURED",
                    "앱 로그인 승인 설정이 준비되지 않았습니다.");
        }
        if (requestId == null || !requestId.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
                || authorization == null || !authorization.startsWith("Bearer ")
                || timestamp == null || !timestamp.matches("[0-9]{1,12}")
                || signature == null || !signature.matches("[0-9a-f]{64}")) {
            throw invalid();
        }
        String token = authorization.substring(7);
        if (token.isEmpty() || token.length() > 16384 || !token.matches("[A-Za-z0-9._-]+")) throw invalid();
        long issuedAt = Long.parseLong(timestamp);
        long now = clock.instant().getEpochSecond();
        if (!Long.toString(issuedAt).equals(timestamp)
                || issuedAt < now - MAX_SKEW_SECONDS || issuedAt > now + MAX_SKEW_SECONDS) throw invalid();

        // 요청·경로·메서드·Bearer hash·시각을 함께 묶는다. 승인 재사용은 DB의 일회용 행 잠금도 차단한다.
        String canonicalId = UUID.fromString(requestId).toString();
        try {
            String bearerHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
            String message = String.join("\n", "mobile-auth-approval-v1", "POST", "/api/v1/auth/mobile/authorize",
                    canonicalId, bearerHash, timestamp);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] expected = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            byte[] received = HexFormat.of().parseHex(signature);
            if (!MessageDigest.isEqual(expected, received)) throw invalid();
        } catch (GeneralSecurityException unavailable) {
            // crypto 예외나 입력 증명을 응답·로그에 포함하지 않는다.
            throw new IllegalStateException("Mobile approval verification is unavailable");
        }
    }

    private AuthException invalid() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "MOBILE_AUTH_APPROVAL_INVALID",
                "웹에서 앱 로그인을 다시 승인해 주세요.");
    }
}
