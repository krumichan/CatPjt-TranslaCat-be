package jp.co.translacat.domain.accountbook.accountbook.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jp.co.translacat.domain.accountbook.accountbook.dto.AccountBookResponseDto;
import jp.co.translacat.domain.accountbook.accountbook.dto.AccountBookSearchRequestDto;
import jp.co.translacat.global.utils.QueryDslUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

import static jp.co.translacat.domain.accountbook.accountbook.entity.QAccountBook.accountBook;

@Repository
@RequiredArgsConstructor
public class AccountBookRepositoryImpl implements AccountBookRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<AccountBookResponseDto> search(Long userId, AccountBookSearchRequestDto condition) {
        return queryFactory
                .select(Projections.constructor(
                        AccountBookResponseDto.class,
                        accountBook.id,
                        accountBook.name,
                        accountBook.description,
                        accountBook.category,
                        accountBook.currency.code,
                        accountBook.currency.symbol,
                        Expressions.constant(BigDecimal.ZERO),
                        Expressions.constant(BigDecimal.ZERO),
                        Expressions.constant(BigDecimal.ZERO)
                ))
                .from(accountBook)
                .join(accountBook.currency)
                .where(
                        accountBook.user.id.eq(userId),
                        accountBook.deleted.isFalse(),
                        keywordContains(condition.getKeyword()),
                        QueryDslUtil.eqIfHasText(
                                accountBook.category,
                                condition.getCategory()
                        )
                )
                .orderBy(accountBook.createdAt.desc())
                .fetch();
    }

    private BooleanExpression keywordContains(String keyword) {
        return QueryDslUtil.anyContainsIgnoreCaseIfHasText(
                keyword,
                accountBook.name,
                accountBook.description
        );
    }
}