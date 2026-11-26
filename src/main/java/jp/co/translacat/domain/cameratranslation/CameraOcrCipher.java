package jp.co.translacat.domain.cameratranslation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/** OCR 입력과 결과를 전용 AES-GCM 키로 보호한다. */
@Component
class CameraOcrCipher {
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    /** 전용 256비트 키가 비어 있으면 OCR 접수만 비활성화한다. */
    CameraOcrCipher(@Value("${camera-translation.ocr-key:}") String configuredKey) {
        if (configuredKey.isBlank()) {
            this.key = null;
            return;
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(configuredKey.trim());
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Camera OCR key must be a base64 256-bit key");
        }
        if (decoded.length != 32) {
            throw new IllegalArgumentException("Camera OCR key must be a base64 256-bit key");
        }
        this.key = new SecretKeySpec(decoded, "AES");
    }

    boolean available() {
        return key != null;
    }

    /** 같은 작업에서도 입력과 결과를 별도 용도로 인증한다. */
    byte[] seal(UUID jobId, String purpose, byte[] plaintext) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        byte[] ciphertext = apply(Cipher.ENCRYPT_MODE, jobId, purpose, nonce, plaintext);
        return ByteBuffer.allocate(nonce.length + ciphertext.length).put(nonce).put(ciphertext).array();
    }

    /** 작업 ID와 용도가 맞는 암호문만 복호화한다. */
    byte[] open(UUID jobId, String purpose, byte[] envelope) {
        if (envelope == null || envelope.length < NONCE_BYTES + TAG_BITS / 8) {
            throw CameraTranslationProblem.unavailable();
        }

        var input = ByteBuffer.wrap(envelope);
        byte[] nonce = new byte[NONCE_BYTES];
        input.get(nonce);
        byte[] ciphertext = new byte[input.remaining()];
        input.get(ciphertext);
        return apply(Cipher.DECRYPT_MODE, jobId, purpose, nonce, ciphertext);
    }

    // 잘못된 키나 수정된 데이터의 원문을 응답이나 로그에 남기지 않는다.
    private byte[] apply(int operation, UUID jobId, String purpose, byte[] nonce, byte[] input) {
        if (key == null) {
            throw CameraTranslationProblem.unavailable();
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(operation, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD((jobId + ":" + purpose).getBytes(StandardCharsets.US_ASCII));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException failure) {
            throw CameraTranslationProblem.unavailable();
        }
    }
}
