package jp.co.translacat.domain.languagelearning.listening.report.entity;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningReportStatus;
import jp.co.translacat.domain.languagelearning.listening.response.entity.ListeningTaskResponse;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "language_learning_listening_evaluation_report",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_listening_report_user_response",
                columnNames = {"user_id", "task_response_id"}
        ),
        indexes = @Index(
                name = "idx_ll_listening_report_status",
                columnList = "status,created_at"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListeningEvaluationReport extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_response_id", nullable = false, updatable = false)
    private ListeningTaskResponse taskResponse;

    @Column(name = "reason_code", nullable = false, length = 100)
    private String reasonCode;

    @Column(length = 2000)
    private String comment;

    @Column(name = "consent_to_retain_audio", nullable = false)
    private boolean consentToRetainAudio;

    @Column(name = "audio_retention_until")
    private LocalDateTime audioRetentionUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ListeningReportStatus status;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    private ListeningEvaluationReport(
            User user,
            ListeningTaskResponse taskResponse,
            String reasonCode,
            String comment,
            boolean consentToRetainAudio,
            LocalDateTime audioRetentionUntil,
            String idempotencyKey
    ) {
        this.user = user;
        this.taskResponse = taskResponse;
        this.reasonCode = reasonCode;
        this.comment = comment;
        this.consentToRetainAudio = consentToRetainAudio;
        this.audioRetentionUntil = audioRetentionUntil;
        this.idempotencyKey = idempotencyKey;
        this.status = ListeningReportStatus.OPEN;
    }

    public static ListeningEvaluationReport create(
            User user,
            ListeningTaskResponse taskResponse,
            String reasonCode,
            String comment,
            boolean consentToRetainAudio,
            LocalDateTime audioRetentionUntil,
            String idempotencyKey
    ) {
        return new ListeningEvaluationReport(
                user,
                taskResponse,
                reasonCode,
                comment,
                consentToRetainAudio,
                audioRetentionUntil,
                idempotencyKey
        );
    }

    public void review(ListeningReportStatus next, LocalDateTime now) {
        status = next;
        reviewedAt = now;
    }
}
