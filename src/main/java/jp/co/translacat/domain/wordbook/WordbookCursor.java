package jp.co.translacat.domain.wordbook;

import jp.co.translacat.domain.wordbook.WordbookTypes.LanguagePair;
import jp.co.translacat.domain.wordbook.WordbookTypes.PageAnchor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

/** 계정·언어 방향에 묶인 페이지 위치를 서명한다. 항목이 지워져도 그 다음 위치를 조회할 수 있다. */
@Component
class WordbookCursor {
    private static final String HMAC = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final SecretKeySpec key;

    /** 공용 서버 비밀을 별도 목적 접두사와 함께 사용하여 모든 인스턴스에서 커서를 읽는다. */
    WordbookCursor(@Value("${jwt.token.secret-key}") String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC);
    }

    /** 기준 항목의 시간과 ID를 현재 계정 및 필터 범위의 커서로 변환한다. */
    String encode(long ownerId, LanguagePair languages, PageAnchor anchor) {
        String position = anchor.createdAt() + "|" + anchor.id();
        byte[] data = position.getBytes(StandardCharsets.UTF_8);
        return ENCODER.encodeToString(data) + "." + ENCODER.encodeToString(signature(ownerId, languages, data));
    }

    /** 다른 계정·필터의 커서, 변경된 서명, 잘못된 위치를 입력 오류로 반환한다. */
    PageAnchor decode(long ownerId, LanguagePair languages, String cursor) {
        if (cursor == null) {
            return null;
        }
        if (cursor.length() > 512) {
            throw WordbookProblem.invalid();
        }

        // 서명을 확인한 다음에만 시간과 ID를 정렬 위치로 해석한다.
        try {
            String[] parts = cursor.split("\\.", -1);
            if (parts.length != 2) {
                throw WordbookProblem.invalid();
            }
            byte[] data = DECODER.decode(parts[0]);
            if (!MessageDigest.isEqual(signature(ownerId, languages, data), DECODER.decode(parts[1]))) {
                throw WordbookProblem.invalid();
            }

            String position = new String(data, StandardCharsets.UTF_8);
            int separator = position.indexOf('|');
            if (separator < 0) {
                throw WordbookProblem.invalid();
            }
            Instant createdAt = Instant.parse(position.substring(0, separator));
            UUID id = WordbookInput.uuid(position.substring(separator + 1));
            return new PageAnchor(createdAt, id);
        } catch (IllegalArgumentException | DateTimeParseException invalid) {
            throw WordbookProblem.invalid();
        }
    }

    // 활동 계정과 필터가 달라지면 같은 위치 데이터도 다른 서명을 얻는다.
    private byte[] signature(long ownerId, LanguagePair languages, byte[] data) {
        String filter = languages == null ? "*" : languages.sourceLanguage() + ">" + languages.targetLanguage();
        String scope = "wordbook-cursor/v1|" + ownerId + "|" + filter + "|";
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(key);
            mac.update(scope.getBytes(StandardCharsets.UTF_8));
            return mac.doFinal(data);
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("Wordbook cursor signature is unavailable", unavailable);
        }
    }
}
