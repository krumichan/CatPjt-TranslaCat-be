package jp.co.translacat.domain.accountbook.fixedcost.repository;

import jp.co.translacat.domain.accountbook.fixedcost.entity.AccountBookFixedCost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountBookFixedCostRepository extends JpaRepository<AccountBookFixedCost, Long> {

    List<AccountBookFixedCost> findByAccountBookIdAndDeletedFalseOrderByActiveDescPaymentDayAscIdDesc(
            Long accountBookId
    );

    List<AccountBookFixedCost> findByAccountBookIdAndActiveTrueAndDeletedFalseOrderByPaymentDayAscIdDesc(
            Long accountBookId
    );
}