package jp.co.translacat.domain.languagelearning.level.model;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * LL에서 확정한 완료 사실이다. JPA Entity나 사용자 입력으로 재사용하지 않는다.
 */
public record LevelCompletionSnapshot(Long userId, Long sessionId, String completionId,
                                      LevelTestSessionType sessionType, int score, String proficiencyBand,
                                      LocalDate completedDate, LocalDateTime startedAt, LocalDateTime completedAt) {
    public LevelCompletionSnapshot {
        if (userId == null
                || userId <= 0
                || sessionId == null
                || sessionId <= 0
                || completionId == null
                || !UUID.fromString(completionId).toString().equals(completionId)
                || sessionType == null
                || score < 0
                || score > 100
                || proficiencyBand == null
                || completedDate == null
                || startedAt == null
                || completedAt == null
                || completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("레벨 테스트 완료 응답 계약이 유효하지 않습니다.");
        }
        String band = score < 40 ? "FOUNDATION" :
                score < 55 ? "BASIC" : score < 70 ? "INTERMEDIATE" : score < 85 ? "UPPER_INTERMEDIATE" : "ADVANCED";
        if (!band.equals(proficiencyBand)) throw new IllegalArgumentException("점수와 레벨 구간이 일치하지 않습니다.");
    }

    public String contentHash() {
        try {
            String value = userId
                    + "|"
                    + sessionId
                    + "|"
                    + completionId
                    + "|"
                    + sessionType
                    + "|"
                    + score
                    + "|"
                    + proficiencyBand
                    + "|"
                    + completedDate
                    + "|"
                    + startedAt
                    + "|"
                    + completedAt;
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
