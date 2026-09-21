package jp.co.translacat.domain.accountbook.transaction.repository;

import jp.co.translacat.domain.accountbook.transaction.entity.ReceiptBatchRegistration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReceiptBatchRegistrationRepository
        extends JpaRepository<ReceiptBatchRegistration, Long> {
    Optional<ReceiptBatchRegistration> findByAccountBookIdAndUserIdAndIdempotencyKey(
            Long accountBookId, Long userId, String idempotencyKey);
}
