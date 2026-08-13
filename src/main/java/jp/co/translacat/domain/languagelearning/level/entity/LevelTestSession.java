package jp.co.translacat.domain.languagelearning.level.entity;

import jakarta.persistence.*;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionStatus;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionType;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "language_learning_level_test_session",
        indexes = @Index(
                name = "idx_ll_level_session_user_status",
                columnList = "user_id,status"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LevelTestSession extends BaseAuditable {

    public static final int DEFAULT_TOTAL_QUESTIONS = 12;
    public static final String DEFAULT_LEVEL_POLICY_VERSION = "LEVEL_BASE_V1";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LevelTestSessionType sessionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LevelTestSessionStatus status;

    @Column(nullable = false)
    private int totalQuestions;

    @Column(nullable = false, length = 50)
    private String levelPolicyVersion = DEFAULT_LEVEL_POLICY_VERSION;

    private Double baseLevelScore;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private LevelTestSession(
            User user,
            LevelTestSessionType sessionType
    ) {
        this.user = user;
        this.sessionType = sessionType;
        this.status = LevelTestSessionStatus.IN_PROGRESS;
        this.totalQuestions = DEFAULT_TOTAL_QUESTIONS;
        this.startedAt = LocalDateTime.now();
    }

    public static LevelTestSession start(
            User user,
            LevelTestSessionType sessionType
    ) {
        return new LevelTestSession(user, sessionType);
    }

    public void complete(double score) {
        this.baseLevelScore = Math.round(score * 100.0) / 100.0;
        this.status = LevelTestSessionStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void fail() {
        this.status = LevelTestSessionStatus.FAILED;
    }
}
