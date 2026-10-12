package jp.co.translacat.domain.user.auth.account;

import jp.co.translacat.domain.user.auth.AuthException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

final class AccountProofTokens {
    private static final SecureRandom RANDOM = new SecureRandom();

    private AccountProofTokens() {
    }

    static String issue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        if (token == null || token.isBlank() || token.length() > 512) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "INVALID_AUTH_PROOF", "인증 확인 정보가 올바르지 않습니다.");
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
