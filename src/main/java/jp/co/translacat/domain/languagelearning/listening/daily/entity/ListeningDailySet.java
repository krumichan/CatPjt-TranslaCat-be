package jp.co.translacat.domain.languagelearning.listening.daily.entity;

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

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningDailySetStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningDifficulty;
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
        name = "language_learning_listening_daily_set",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_listening_set_user_date_language",
                columnNames = {"user_id", "learning_date", "learning_language"}
        ),
        indexes = {
                @Index(
                        name = "idx_ll_listening_set_user_date",
                        columnList = "user_id,learning_date"
                ),
                @Index(
                        name = "idx_ll_listening_set_status",
                        columnList = "status,created_at"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListeningDailySet extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "learning_date", nullable = false, updatable = false)
    private LocalDate learningDate;

    @Column(name = "origin_language", nullable = false, length = 20)
    private String originLanguage;

    @Column(name = "learning_language", nullable = false, length = 20)
    private String learningLanguage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ListeningDifficulty difficulty;

    @Lob
    @Column(name = "topic_snapshot", nullable = false, columnDefinition = "TEXT")
    private String topicSnapshotJson;

    @Lob
    @Column(name = "keyword_snapshot", nullable = false, columnDefinition = "TEXT")
    private String keywordSnapshotJson;

    @Lob
    @Column(name = "profile_snapshot", nullable = false, columnDefinition = "TEXT")
    private String profileSnapshotJson;

    @Column(name = "policy_version", nullable = false, length = 100)
    private String policyVersion;

    @Column(name = "generation_version", length = 100)
    private String generationVersion;

    @Column(name = "target_item_count", nullable = false)
    private int targetItemCount;

    @Column(name = "physical_item_count", nullable = false)
    private int physicalItemCount;

    @Column(name = "completed_item_count", nullable = false)
    private int completedItemCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ListeningDailySetStatus status;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Version
    private long version;

    private ListeningDailySet(
            User user,
            LocalDate learningDate,
            String originLanguage,
            String learningLanguage,
            ListeningDifficulty difficulty,
            String topicSnapshotJson,
            String keywordSnapshotJson,
            String profileSnapshotJson,
            String policyVersion,
            int targetItemCount
    ) {
        this.user = user;
        this.learningDate = learningDate;
        this.originLanguage = originLanguage;
        this.learningLanguage = learningLanguage;
        this.difficulty = difficulty;
        this.topicSnapshotJson = json(topicSnapshotJson, "{}");
        this.keywordSnapshotJson = json(keywordSnapshotJson, "[]");
        this.profileSnapshotJson = json(profileSnapshotJson, "[]");
        this.policyVersion = policyVersion;
        this.targetItemCount = targetItemCount;
        this.status = ListeningDailySetStatus.GENERATING;
    }

    public static ListeningDailySet create(
            User user,
            LocalDate learningDate,
            String originLanguage,
            String learningLanguage,
            ListeningDifficulty difficulty,
            String topicSnapshotJson,
            String keywordSnapshotJson,
            String profileSnapshotJson,
            String policyVersion,
            int targetItemCount
    ) {
        if (targetItemCount < 1 || targetItemCount > 20) {
            throw new IllegalArgumentException(
                    "Listening targetItemCount는 1~20이어야 합니다."
            );
        }
        return new ListeningDailySet(
                user,
                learningDate,
                originLanguage,
                learningLanguage,
                difficulty,
                topicSnapshotJson,
                keywordSnapshotJson,
                profileSnapshotJson,
                policyVersion,
                targetItemCount
        );
    }

    public void recordPhysicalItem() {
        physicalItemCount++;
    }

    public void ready(String generationVersion, boolean partial) {
        this.generationVersion = generationVersion;
        this.failureReason = null;
        this.status = partial
                ? ListeningDailySetStatus.PARTIAL
                : ListeningDailySetStatus.READY;
    }

    public void generated(String generationVersion) {
        this.generationVersion = generationVersion;
        this.failureReason = null;
    }

    public void fail(String reason) {
        this.failureReason = trim(reason);
        this.status = ListeningDailySetStatus.FAILED;
    }

    public void registerCompletedLearning() {
        if (completedItemCount < targetItemCount) {
            completedItemCount++;
        }
        if (completedItemCount >= targetItemCount) {
            status = ListeningDailySetStatus.COMPLETED;
            completedAt = LocalDateTime.now();
        }
    }

    public boolean isUsable() {
        return status == ListeningDailySetStatus.READY
                || status == ListeningDailySetStatus.PARTIAL
                || status == ListeningDailySetStatus.COMPLETED;
    }

    private static String json(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String trim(String value) {
        if (value == null || value.length() <= 1000) {
            return value;
        }
        return value.substring(0, 1000);
    }
}
