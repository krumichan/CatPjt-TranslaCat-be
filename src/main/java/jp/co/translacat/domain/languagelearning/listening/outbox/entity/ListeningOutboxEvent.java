package jp.co.translacat.domain.languagelearning.listening.outbox.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningOutboxStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningOutboxType;
import jp.co.translacat.global.jpa.BaseAuditable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "language_learning_listening_outbox",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ll_listening_outbox_key",
                columnNames = "idempotency_key"
        ),
        indexes = {
                @Index(
                        name = "idx_ll_listening_outbox_delivery",
                        columnList = "status,available_at,created_at"
                ),
                @Index(
                        name = "idx_ll_listening_outbox_lease",
                        columnList = "status,updated_at"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ListeningOutboxEvent extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private ListeningOutboxType eventType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "idempotency_key", nullable = false, length = 240)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ListeningOutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "available_at", nullable = false)
    private LocalDateTime availableAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Version
    private long version;

    private ListeningOutboxEvent(
            ListeningOutboxType eventType,
            Long aggregateId,
            String payloadJson,
            String idempotencyKey,
            LocalDateTime now
    ) {
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payloadJson = payloadJson == null ? "{}" : payloadJson;
        this.idempotencyKey = idempotencyKey;
        this.status = ListeningOutboxStatus.PENDING;
        this.availableAt = now;
    }

    public static ListeningOutboxEvent create(
            ListeningOutboxType eventType,
            Long aggregateId,
            String payloadJson,
            String idempotencyKey,
            LocalDateTime now
    ) {
        return new ListeningOutboxEvent(
                eventType,
                aggregateId,
                payloadJson,
                idempotencyKey,
                now
        );
    }

    public void claim() {
        if (status != ListeningOutboxStatus.PENDING) {
            throw new IllegalStateException(
                    "처리 대기 Listening Outbox가 아닙니다."
            );
        }
        status = ListeningOutboxStatus.PROCESSING;
        attemptCount++;
    }

    public void succeed(LocalDateTime now) {
        status = ListeningOutboxStatus.SUCCEEDED;
        processedAt = now;
        lastError = null;
    }

    public void reclaim(LocalDateTime now) {
        if (status != ListeningOutboxStatus.PROCESSING) {
            return;
        }
        status = ListeningOutboxStatus.PENDING;
        availableAt = now;
        lastError = "PROCESSING lease가 만료되어 재처리합니다.";
    }

    public void fail(
            String reason,
            boolean retryable,
            Duration retryAfter,
            int automaticRetryLimit,
            LocalDateTime now
    ) {
        lastError = trim(reason);
        if (retryable && attemptCount <= automaticRetryLimit) {
            status = ListeningOutboxStatus.PENDING;
            availableAt = now.plus(
                    retryAfter == null ? Duration.ofSeconds(1) : retryAfter
            );
            return;
        }
        status = ListeningOutboxStatus.FAILED;
        processedAt = now;
    }

    private String trim(String value) {
        if (value == null || value.length() <= 1000) {
            return value;
        }
        return value.substring(0, 1000);
    }
}
