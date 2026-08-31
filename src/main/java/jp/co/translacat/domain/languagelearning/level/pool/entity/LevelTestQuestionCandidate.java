package jp.co.translacat.domain.languagelearning.level.pool.entity;

import jakarta.persistence.*;

import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "language_learning_level_test_question_candidate",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_level_candidate_session_question_band",
                columnNames = {
                        "session_id",
                        "question_number",
                        "complexity_band"
                }
        ),
        indexes = {
                @Index(
                        name = "idx_ll_level_candidate_lookup",
                        columnList = "session_id,question_number,complexity_band,status"
                ),
                @Index(
                        name = "idx_ll_level_candidate_pool",
                        columnList = "pool_question_id"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LevelTestQuestionCandidate extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "question_number", nullable = false)
    private int questionNumber;

    @Column(name = "complexity_band", nullable = false)
    private int complexityBand;

    @Column(name = "pool_question_id")
    private Long poolQuestionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LevelTestQuestionCandidateStatus status;

    @Column(name = "selected_at")
    private LocalDateTime selectedAt;

    private LevelTestQuestionCandidate(
            Long sessionId,
            int questionNumber,
            int complexityBand,
            Long poolQuestionId
    ) {
        this.sessionId = sessionId;
        this.questionNumber = questionNumber;
        this.complexityBand = complexityBand;
        this.poolQuestionId = poolQuestionId;
        this.status = poolQuestionId == null
                ? LevelTestQuestionCandidateStatus.GENERATING
                : LevelTestQuestionCandidateStatus.AVAILABLE;
    }

    public static LevelTestQuestionCandidate create(
            Long sessionId,
            int questionNumber,
            int complexityBand,
            Long poolQuestionId
    ) {
        return new LevelTestQuestionCandidate(
                sessionId,
                questionNumber,
                complexityBand,
                poolQuestionId
        );
    }

    public void markAvailable(Long poolQuestionId) {
        this.poolQuestionId = poolQuestionId;
        this.status = LevelTestQuestionCandidateStatus.AVAILABLE;
    }

    public void markFailed() {
        if (status == LevelTestQuestionCandidateStatus.GENERATING) {
            status = LevelTestQuestionCandidateStatus.FAILED;
        }
    }

    public void select(LocalDateTime now) {
        status = LevelTestQuestionCandidateStatus.SELECTED;
        selectedAt = now;
    }

    public void expire() {
        if (status != LevelTestQuestionCandidateStatus.SELECTED) {
            status = LevelTestQuestionCandidateStatus.EXPIRED;
        }
    }
}
