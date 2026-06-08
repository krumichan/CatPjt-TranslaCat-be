package jp.co.translacat.domain.accountbook.transaction.repository;

import jp.co.translacat.domain.accountbook.transaction.entity.AccountBookTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountBookTransactionRepository
        extends JpaRepository<AccountBookTransaction, Long>,
        AccountBookTransactionRepositoryCustom {
}