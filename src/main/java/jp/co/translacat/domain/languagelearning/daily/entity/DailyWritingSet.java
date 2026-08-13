package jp.co.translacat.domain.languagelearning.daily.entity;

import jakarta.persistence.*;

import jp.co.translacat.domain.languagelearning.common.enums.DailySetStatus;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "language_learning_daily_set",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_daily_set_user_date",
                columnNames = {"user_id", "learning_date"}
        ),
        indexes = @Index(
                name = "idx_ll_daily_set_user_date",
                columnList = "user_id,learning_date"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyWritingSet extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "learning_date", nullable = false)
    private LocalDate learningDate;

    @Column(name = "snapshot_id", nullable = false, unique = true, length = 100)
    private String snapshotId;

    @Column(nullable = false)
    private int sentenceCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DailySetStatus status;

    @Lob
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String snapshotJson;

    @Column(name = "prompt_version", length = 100)
    private String promptVersion;

    @Column(nullable = false)
    private int regenerationCount;

    private LocalDateTime completedAt;

    @Column(length = 1000)
    private String failureMessage;

    private DailyWritingSet(
            User user,
            LocalDate learningDate,
            String snapshotId,
            int sentenceCount,
            String snapshotJson
    ) {
        this.user = user;
        this.learningDate = learningDate;
        this.snapshotId = snapshotId;
        this.sentenceCount = sentenceCount;
        this.snapshotJson = snapshotJson;
        this.status = DailySetStatus.GENERATING;
    }

    public static DailyWritingSet createGenerating(
            User user,
            LocalDate learningDate,
            String snapshotId,
            int sentenceCount,
            String snapshotJson
    ) {
        return new DailyWritingSet(
                user,
                learningDate,
                snapshotId,
                sentenceCount,
                snapshotJson
        );
    }

    public void ready(String promptVersion) {
        this.promptVersion = promptVersion;
        this.status = DailySetStatus.READY;
        this.failureMessage = null;
    }

    public void fail(String message) {
        this.status = DailySetStatus.FAILED;
        this.failureMessage = message;
    }

    public void restartGeneration(String snapshotJson) {
        this.snapshotJson = snapshotJson;
        this.status = DailySetStatus.GENERATING;
        this.failureMessage = null;
    }

    public void incrementRegeneration() {
        this.regenerationCount++;
    }

    public void complete() {
        this.status = DailySetStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }
}
