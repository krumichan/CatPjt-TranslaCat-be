package jp.co.translacat.domain.languagelearning.profile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jp.co.translacat.domain.languagelearning.level.model.LevelCompletionSnapshot;

import java.time.LocalDateTime;

/**
 * LL 완료 사실을 Core Profile에 중복 적용하지 않기 위한 호환 수신 기록이다.
 */
@Entity
@Table(name = "language_learning_level_baseline_receipt")
public class LevelTestBaselineReceipt {
    @Id
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "completion_id", nullable = false, length = 36)
    private String completionId;
    @Column(name = "completion_hash", nullable = false, length = 64)
    private String completionHash;
    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;
    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;

    protected LevelTestBaselineReceipt() {
    }

    public LevelTestBaselineReceipt(LevelCompletionSnapshot value, LocalDateTime now) {
        this.userId = value.userId();
        update(value, now);
    }

    public void update(LevelCompletionSnapshot value, LocalDateTime now) {
        if (!userId.equals(value.userId())) throw new IllegalArgumentException("기준점 소유자가 다릅니다.");
        this.completionId = value.completionId();
        this.completionHash = value.contentHash();
        this.completedAt = value.completedAt();
        this.appliedAt = now;
    }

    public String completionId() {
        return completionId;
    }

    public String completionHash() {
        return completionHash;
    }

    public LocalDateTime completedAt() {
        return completedAt;
    }
}
