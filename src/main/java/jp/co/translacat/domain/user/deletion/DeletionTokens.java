package jp.co.translacat.domain.user.deletion;

import jp.co.translacat.domain.user.auth.AuthException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

final class DeletionTokens {
    private static final SecureRandom RANDOM = new SecureRandom();

    static String issue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String secret(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{43}")) throw invalid();
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            if (decoded.length != 32 || !Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(value)) throw invalid();
        } catch (IllegalArgumentException ignored) {
            throw invalid();
        }
        return value;
    }

    static String uuid(String value) {
        try {
            if (value == null || !UUID.fromString(value).toString().equals(value)) throw invalid();
            return value;
        } catch (IllegalArgumentException ignored) {
            throw invalid();
        }
    }

    static String hash(String value) {
        if (value == null) throw invalid();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required");
        }
    }

    static boolean same(String first, String second) {
        return first != null && second != null && MessageDigest.isEqual(first.getBytes(StandardCharsets.US_ASCII), second.getBytes(StandardCharsets.US_ASCII));
    }

    static AuthException invalid() {
        return new AuthException(HttpStatus.BAD_REQUEST, "ACCOUNT_DELETION_INVALID_INPUT", "계정 삭제 입력을 확인해 주세요.");
    }
}
