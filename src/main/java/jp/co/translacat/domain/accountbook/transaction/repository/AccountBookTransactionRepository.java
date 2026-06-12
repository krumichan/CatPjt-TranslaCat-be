package jp.co.translacat.domain.accountbook.transaction.repository;

import jp.co.translacat.domain.accountbook.transaction.entity.AccountBookTransaction;
import jp.co.translacat.domain.accountbook.transaction.enums.AccountBookTransactionSourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountBookTransactionRepository
        extends JpaRepository<AccountBookTransaction, Long>,
        AccountBookTransactionRepositoryCustom {

    Optional<AccountBookTransaction> findByIdAndAccountBookId(
            Long transactionId,
            Long accountBookId
    );

    boolean existsByAccountBookIdAndSourceTypeAndSourceIdAndSourceYearAndSourceMonth(
            Long accountBookId,
            AccountBookTransactionSourceType sourceType,
            Long sourceId,
            Integer sourceYear,
            Integer sourceMonth
    );
}