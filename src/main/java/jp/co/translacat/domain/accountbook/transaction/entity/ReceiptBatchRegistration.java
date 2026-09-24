package jp.co.translacat.domain.accountbook.transaction.entity;

import jakarta.persistence.*;

import jp.co.translacat.global.jpa.Base;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "receipt_batch_registration",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_receipt_batch_idempotency",
                columnNames = {"account_book_id", "user_id", "idempotency_key"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReceiptBatchRegistration extends Base {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_book_id", nullable = false)
    private Long accountBookId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "payload_fingerprint", nullable = false, length = 64)
    private String payloadFingerprint;

    @Lob
    @Column(name = "response_json", columnDefinition = "LONGTEXT")
    private String responseJson;

    public static ReceiptBatchRegistration claim(
            Long accountBookId, Long userId, String key, String fingerprint) {
        ReceiptBatchRegistration value = new ReceiptBatchRegistration();
        value.accountBookId = accountBookId;
        value.userId = userId;
        value.idempotencyKey = key;
        value.payloadFingerprint = fingerprint;
        return value;
    }

    public void complete(String responseJson) {
        if (responseJson == null || responseJson.isBlank()) {
            throw new IllegalArgumentException("An idempotent response snapshot is required.");
        }
        this.responseJson = responseJson;
    }
}
