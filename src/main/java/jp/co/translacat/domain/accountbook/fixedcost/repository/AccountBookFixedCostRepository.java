package jp.co.translacat.domain.accountbook.fixedcost.repository;

import jp.co.translacat.domain.accountbook.fixedcost.entity.AccountBookFixedCost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AccountBookFixedCostRepository extends JpaRepository<AccountBookFixedCost, Long> {

    List<AccountBookFixedCost> findByAccountBookIdAndDeletedFalseOrderByActiveDescPaymentDayAscIdDesc(
            Long accountBookId
    );

    List<AccountBookFixedCost> findByAccountBookIdAndActiveTrueAndDeletedFalseOrderByPaymentDayAscIdDesc(
            Long accountBookId
    );

    @Query("""
        select distinct fixedCost.accountBook.id
        from AccountBookFixedCost fixedCost
        where fixedCost.active = true
          and fixedCost.deleted = false
          and fixedCost.startMonth <= :targetMonth
          and (
              fixedCost.endMonth is null
              or fixedCost.endMonth >= :targetMonth
          )
        """)
    List<Long> findGenerationTargetAccountBookIds(
            @Param("targetMonth") LocalDate targetMonth
    );
}