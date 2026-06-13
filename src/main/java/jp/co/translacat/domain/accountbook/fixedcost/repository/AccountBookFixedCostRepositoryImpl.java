package jp.co.translacat.domain.accountbook.fixedcost.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jp.co.translacat.domain.accountbook.fixedcost.entity.QAccountBookFixedCost;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class AccountBookFixedCostRepositoryImpl implements AccountBookFixedCostRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    private final QAccountBookFixedCost fixedCost =
            QAccountBookFixedCost.accountBookFixedCost;

    @Override
    public List<Long> findGenerationTargetAccountBookIds(LocalDate targetMonth) {
        return queryFactory
                .select(fixedCost.accountBook.id)
                .distinct()
                .from(fixedCost)
                .where(
                        fixedCost.active.isTrue(),
                        fixedCost.deleted.isFalse(),
                        fixedCost.startMonth.loe(targetMonth),
                        endMonthIsNullOrAfter(targetMonth)
                )
                .fetch();
    }

    private BooleanExpression endMonthIsNullOrAfter(LocalDate targetMonth) {
        return fixedCost.endMonth.isNull()
                .or(fixedCost.endMonth.goe(targetMonth));
    }
}