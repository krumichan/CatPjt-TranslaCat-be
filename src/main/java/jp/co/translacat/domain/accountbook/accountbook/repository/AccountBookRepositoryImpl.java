package jp.co.translacat.domain.accountbook.accountbook.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jp.co.translacat.domain.accountbook.accountbook.dto.AccountBookResponseDto;
import jp.co.translacat.domain.accountbook.accountbook.dto.AccountBookSearchRequestDto;
import jp.co.translacat.domain.accountbook.transaction.enums.AccountBookTransactionType;
import jp.co.translacat.global.utils.QueryDslUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

import static jp.co.translacat.domain.accountbook.accountbook.entity.QAccountBook.accountBook;
import static jp.co.translacat.domain.accountbook.member.entity.QAccountBookMember.accountBookMember;
import static jp.co.translacat.domain.accountbook.transaction.entity.QAccountBookTransaction.accountBookTransaction;

@Repository
@RequiredArgsConstructor
public class AccountBookRepositoryImpl implements AccountBookRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<AccountBookResponseDto> search(
            Long userId,
            AccountBookSearchRequestDto condition
    ) {
        NumberExpression<BigDecimal> incomeAmountExpression =
                amountExpression(AccountBookTransactionType.INCOME);

        NumberExpression<BigDecimal> expenseAmountExpression =
                amountExpression(AccountBookTransactionType.EXPENSE);

        NumberExpression<BigDecimal> balanceExpression =
                incomeAmountExpression.subtract(expenseAmountExpression);

        NumberExpression<Long> transactionCountExpression =
                accountBookTransaction.id.countDistinct();

        return queryFactory
                .select(
                        Projections.constructor(
                                AccountBookResponseDto.class,
                                accountBook.id,
                                accountBook.name,
                                accountBook.description,
                                accountBook.category,
                                accountBook.currency.code,
                                accountBook.currency.symbol,
                                incomeAmountExpression,
                                expenseAmountExpression,
                                balanceExpression,
                                transactionCountExpression,
                                accountBookMember.role
                        )
                )
                .from(accountBook)
                .join(accountBook.currency)
                .join(accountBookMember)
                .on(
                        accountBookMember.accountBook.eq(accountBook),
                        accountBookMember.user.id.eq(userId),
                        accountBookMember.deleted.isFalse()
                )
                .leftJoin(accountBookTransaction)
                .on(accountBookTransaction.accountBook.eq(accountBook))
                .where(
                        accountBook.deleted.isFalse(),
                        QueryDslUtil.anyContainsIgnoreCaseIfHasText(
                                condition.getKeyword(),
                                accountBook.name,
                                accountBook.description
                        ),
                        QueryDslUtil.eqIfNotNull(accountBook.category, condition.getCategory())
                )
                .groupBy(
                        accountBook.id,
                        accountBook.name,
                        accountBook.description,
                        accountBook.category,
                        accountBook.currency.code,
                        accountBook.currency.symbol,
                        accountBookMember.role,
                        accountBook.createdAt
                )
                .orderBy(accountBook.createdAt.desc())
                .fetch();
    }

    private NumberExpression<BigDecimal> amountExpression(AccountBookTransactionType type) {
        return new CaseBuilder()
                .when(accountBookTransaction.type.eq(type))
                .then(accountBookTransaction.amount)
                .otherwise(BigDecimal.ZERO)
                .sum()
                .coalesce(BigDecimal.ZERO);
    }
}