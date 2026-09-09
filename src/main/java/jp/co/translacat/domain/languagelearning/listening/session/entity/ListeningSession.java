package jp.co.translacat.domain.languagelearning.listening.session.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningSessionStatus;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningDailySet;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.stream.IntStream;

@Entity
@Getter
@Table(
        name = "language_learning_listening_session",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_ll_listening_session_user_key",
                        columnNames = {"user_id", "idempotency_key"}
                ),
                @UniqueConstraint(
                        name = "uk_ll_listening_session_user_active",
                        columnNames = {"user_id", "active_key"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_ll_listening_session_user_status",
                        columnList = "user_id,status,last_activity_at"
                ),
                @Index(
                        name = "idx_ll_listening_session_set",
                        columnList = "daily_set_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListeningSession extends BaseAuditable {

    private static final String ACTIVE_KEY = "ACTIVE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_set_id", nullable = false, updatable = false)
    private ListeningDailySet dailySet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ListeningSessionStatus status;

    @Column(name = "active_key", length = 10)
    private String activeKey;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "last_activity_at", nullable = false)
    private LocalDateTime lastActivityAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Lob
    @Column(name = "selected_task_types", nullable = false, columnDefinition = "TEXT")
    private String selectedTaskTypesJson;

    @Lob
    @Column(name = "policy_snapshot", nullable = false, columnDefinition = "TEXT")
    private String policySnapshotJson;

    @Lob
    @Column(name = "selection_snapshot", nullable = false, columnDefinition = "TEXT")
    private String selectionSnapshotJson;

    @Column(name = "completed_item_count", nullable = false)
    private int completedItemCount;

    @Column(name = "evaluated_item_count", nullable = false)
    private int evaluatedItemCount;

    @Column(name = "actual_duration_ms", nullable = false)
    private long actualDurationMs;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;

    @Version
    private long version;

    private ListeningSession(
            User user,
            ListeningDailySet dailySet,
            String selectedTaskTypesJson,
            String policySnapshotJson,
            String selectionSnapshotJson,
            String idempotencyKey,
            LocalDateTime now
    ) {
        this.user = user;
        this.dailySet = dailySet;
        this.selectedTaskTypesJson = selectedTaskTypesJson;
        this.policySnapshotJson = policySnapshotJson;
        this.selectionSnapshotJson = selectionSnapshotJson;
        this.idempotencyKey = idempotencyKey;
        this.status = ListeningSessionStatus.IN_PROGRESS;
        this.activeKey = ACTIVE_KEY;
        this.startedAt = now;
        this.lastActivityAt = now;
    }

    public static ListeningSession create(
            User user,
            ListeningDailySet dailySet,
            String selectedTaskTypesJson,
            String policySnapshotJson,
            String selectionSnapshotJson,
            String idempotencyKey,
            LocalDateTime now
    ) {
        return new ListeningSession(
                user,
                dailySet,
                selectedTaskTypesJson,
                policySnapshotJson,
                selectionSnapshotJson,
                idempotencyKey,
                now
        );
    }

    public boolean isActive() {
        return status == ListeningSessionStatus.IN_PROGRESS;
    }

    public boolean hasAllTargetItems(Collection<Integer> officialItemIndices) {
        int target = dailySet.getTargetItemCount();
        return target > 0 && IntStream.rangeClosed(1, target)
                .allMatch(officialItemIndices::contains);
    }

    public void updateSelectionSnapshot(String selectionSnapshotJson) {
        requireActive();
        this.selectionSnapshotJson = selectionSnapshotJson;
    }

    public boolean isExpired(LocalDateTime now, Duration resumeWindow) {
        return isActive()
                && lastActivityAt.plus(resumeWindow).isBefore(now);
    }

    public void touch(LocalDateTime now) {
        requireActive();
        lastActivityAt = now;
    }

    public void recordLearning(
            boolean evaluated,
            long durationMs,
            LocalDateTime now
    ) {
        requireLearningRecordable();
        completedItemCount++;
        if (evaluated) {
            evaluatedItemCount++;
        }
        actualDurationMs += Math.max(0, durationMs);
        lastActivityAt = now;
    }

    public void startEvaluating(LocalDateTime now) {
        if (status == ListeningSessionStatus.EVALUATING
                || status == ListeningSessionStatus.COMPLETED) {
            return;
        }
        requireActive();
        status = ListeningSessionStatus.EVALUATING;
        activeKey = null;
        lastActivityAt = now;
    }

    /**
     * Re-enters the background evaluation phase when a failed Task is manually
     * retried after the Session has already reached COMPLETED.
     *
     * <p>The active slot must stay released: a manual evaluation retry is a
     * background recovery operation and must not block the user from starting
     * another Listening mode.</p>
     */
    public void resumeEvaluationForRetry(LocalDateTime now) {
        if (status == ListeningSessionStatus.IN_PROGRESS) {
            lastActivityAt = now;
            return;
        }
        if (status == ListeningSessionStatus.EVALUATING) {
            lastActivityAt = now;
            return;
        }
        if (status == ListeningSessionStatus.COMPLETED) {
            status = ListeningSessionStatus.EVALUATING;
            activeKey = null;
            completedAt = null;
            lastActivityAt = now;
            return;
        }
        throw new IllegalStateException(
                "평가 재시도를 시작할 수 없는 Listening Session입니다."
        );
    }

    public void complete(LocalDateTime now) {
        if (status != ListeningSessionStatus.IN_PROGRESS
                && status != ListeningSessionStatus.EVALUATING) {
            if (status == ListeningSessionStatus.COMPLETED) {
                return;
            }
            throw new IllegalStateException(
                    "완료할 수 없는 Listening Session입니다."
            );
        }
        status = ListeningSessionStatus.COMPLETED;
        activeKey = null;
        completedAt = now;
        lastActivityAt = now;
    }

    public void abandon(LocalDateTime now) {
        if (!isActive()) {
            return;
        }
        status = ListeningSessionStatus.ABANDONED;
        activeKey = null;
        completedAt = now;
        lastActivityAt = now;
    }

    private void requireLearningRecordable() {
        if (status != ListeningSessionStatus.IN_PROGRESS
                && status != ListeningSessionStatus.EVALUATING
                && status != ListeningSessionStatus.COMPLETED
                && status != ListeningSessionStatus.ABANDONED) {
            throw new IllegalStateException(
                    "학습 결과를 반영할 수 없는 Listening Session입니다."
            );
        }
    }

    private void requireActive() {
        if (!isActive()) {
            throw new IllegalStateException(
                    "활성 Listening Session이 아닙니다."
            );
        }
    }
}
