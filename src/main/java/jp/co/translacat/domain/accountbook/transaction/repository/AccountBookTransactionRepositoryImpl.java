package jp.co.translacat.domain.accountbook.transaction.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookTransactionRequestDto;
import jp.co.translacat.domain.accountbook.transaction.dto.AccountBookTransactionResponseDto;
import jp.co.translacat.global.utils.PagingUtil;
import jp.co.translacat.global.utils.QueryDslUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static jp.co.translacat.domain.accountbook.transaction.entity.QAccountBookTransaction.accountBookTransaction;

@Repository
@RequiredArgsConstructor
public class AccountBookTransactionRepositoryImpl implements AccountBookTransactionRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<AccountBookTransactionResponseDto> findAllWithPage(
            Long accountBookId,
            AccountBookTransactionRequestDto condition
    ) {
        LocalDate startDate = null;
        LocalDate endDate = null;

        if (condition.getYear() != null && condition.getMonth() != null) {
            YearMonth yearMonth = YearMonth.of(condition.getYear(), condition.getMonth());
            startDate = yearMonth.atDay(1);
            endDate = yearMonth.atEndOfMonth();
        }

        BooleanBuilder where = new BooleanBuilder();

        where.and(QueryDslUtil.eqIfNotNull(
                accountBookTransaction.accountBook.id,
                accountBookId
        ));

        where.and(QueryDslUtil.betweenIfNotNull(
                accountBookTransaction.transactionDate,
                startDate,
                endDate
        ));

        List<AccountBookTransactionResponseDto> content = queryFactory
                .select(Projections.constructor(
                        AccountBookTransactionResponseDto.class,
                        accountBookTransaction.id,
                        accountBookTransaction.type,
                        accountBookTransaction.amount,
                        accountBookTransaction.title,
                        accountBookTransaction.storeName,
                        accountBookTransaction.category,
                        accountBookTransaction.transactionDate,
                        accountBookTransaction.memo,
                        accountBookTransaction.createdAt
                ))
                .from(accountBookTransaction)
                .where(where)
                .orderBy(
                        accountBookTransaction.transactionDate.desc(),
                        accountBookTransaction.createdAt.desc(),
                        accountBookTransaction.id.desc()
                )
                .offset(condition.getOffset())
                .limit(condition.getPageSize())
                .fetch();

        Long total = queryFactory
                .select(accountBookTransaction.count())
                .from(accountBookTransaction)
                .where(where)
                .fetchOne();

        return PagingUtil.toPage(
                content,
                total != null ? total : 0L,
                condition
        );
    }
}