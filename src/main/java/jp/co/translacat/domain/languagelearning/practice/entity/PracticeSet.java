package jp.co.translacat.domain.languagelearning.practice.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeSetStatus;
import jp.co.translacat.domain.languagelearning.practice.enums.PracticeGenerationStatus;
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
        name = "language_learning_practice_set",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_practice_set_user_date_domain_mode",
                columnNames = {"user_id", "learning_date", "domain", "mode"}
        ),
        indexes = {
                @Index(name = "idx_ll_practice_set_user_date", columnList = "user_id,learning_date"),
                @Index(name = "idx_ll_practice_set_user_domain", columnList = "user_id,domain"),
                @Index(name = "idx_ll_practice_set_generation", columnList = "generation_status,generation_started_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PracticeSet extends BaseAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "learning_date", nullable = false)
    private LocalDate learningDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PracticeDomain domain;

    @Column(nullable = false, length = 40)
    private String mode;

    @Column(name = "origin_language", nullable = false, length = 20)
    private String originLanguage;

    @Column(name = "learning_language", nullable = false, length = 20)
    private String learningLanguage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PracticeSetStatus status = PracticeSetStatus.ACTIVE;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column(name = "complexity_band", nullable = false)
    private int complexityBand;

    @Column(name = "prompt_version", length = 100)
    private String promptVersion;

    @Column(name = "official_score")
    private Double officialScore;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_status", length = 20)
    private PracticeGenerationStatus generationStatus;

    @Lob
    @Column(name = "generation_request_json", columnDefinition = "LONGTEXT")
    private String generationRequestJson;

    @Column(name = "generation_token", length = 36)
    private String generationToken;

    @Column(name = "generation_started_at")
    private LocalDateTime generationStartedAt;

    @Column(name = "generation_failure_message", length = 1000)
    private String generationFailureMessage;

    private PracticeSet(
            User user,
            LocalDate learningDate,
            PracticeDomain domain,
            String mode,
            String originLanguage,
            String learningLanguage,
            int questionCount,
            int complexityBand
    ) {
        this.user = user;
        this.learningDate = learningDate;
        this.domain = domain;
        this.mode = mode;
        this.originLanguage = originLanguage;
        this.learningLanguage = learningLanguage;
        this.questionCount = questionCount;
        this.complexityBand = complexityBand;
        this.startedAt = LocalDateTime.now();
    }

    public static PracticeSet create(
            User user,
            LocalDate learningDate,
            PracticeDomain domain,
            String mode,
            String originLanguage,
            String learningLanguage,
            int questionCount,
            int complexityBand
    ) {
        return new PracticeSet(
                user, learningDate, domain, mode, originLanguage,
                learningLanguage, questionCount, complexityBand
        );
    }

    public void markGenerated(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public PracticeGenerationStatus getGenerationStatus() {
        // Sets created before incremental generation already contain their full question list.
        return generationStatus == null ? PracticeGenerationStatus.READY : generationStatus;
    }

    public void queueGeneration(String requestJson) {
        this.generationRequestJson = requestJson;
        resumeGeneration();
    }

    public void resumeGeneration() {
        this.generationStatus = PracticeGenerationStatus.PENDING;
        this.generationToken = null;
        this.generationStartedAt = null;
        this.generationFailureMessage = null;
    }

    public void claimGeneration(String token, LocalDateTime now) {
        this.generationStatus = PracticeGenerationStatus.GENERATING;
        this.generationToken = token;
        this.generationStartedAt = now;
    }

    public boolean ownsGeneration(String token) {
        return getGenerationStatus() == PracticeGenerationStatus.GENERATING
                && token != null && token.equals(generationToken);
    }

    public void finishGeneration() {
        this.generationStatus = PracticeGenerationStatus.READY;
        this.generationToken = null;
        this.generationStartedAt = null;
        this.generationFailureMessage = null;
    }

    public void failGeneration(String message, boolean hasQuestions) {
        this.generationStatus = hasQuestions
                ? PracticeGenerationStatus.PARTIAL : PracticeGenerationStatus.FAILED;
        this.generationToken = null;
        this.generationStartedAt = null;
        String reason = message == null || message.isBlank() ? "Question generation failed." : message;
        this.generationFailureMessage = reason.substring(0, Math.min(1000, reason.length()));
    }

    public void complete(double officialScore) {
        if (this.status == PracticeSetStatus.COMPLETED) {
            return;
        }
        this.status = PracticeSetStatus.COMPLETED;
        this.officialScore = Math.max(0, Math.min(100, officialScore));
        this.completedAt = LocalDateTime.now();
    }
}
