package jp.co.translacat.infrastructure.languagelearning.resultjournal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** 전송 경계의 순수 검증·재시도 정책이다. 업무 점수 계산은 하지 않는다. */
public final class ResultDeliveryRules {
    public static final int MAX_PAYLOAD_BYTES = 262_144;
    private ResultDeliveryRules() { }
    public static String hash(String payload) {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("결과 원장 본문 크기 제한을 초과했습니다.");
        }
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256을 사용할 수 없습니다."); }
    }
    public static String sourceId(String value) {
        if (value == null || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new IllegalArgumentException("환경별 고정 UUID source-instance-id가 필요합니다.");
        }
        UUID.fromString(value);
        return value;
    }
    public static long retryDelaySeconds(int attempt) {
        if (attempt < 1) throw new IllegalArgumentException("시도 횟수는 양수여야 합니다.");
        return Math.min(300, 1L << Math.min(attempt, 9));
    }
}
