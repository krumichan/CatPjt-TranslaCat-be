package jp.co.translacat.domain.accountbook.receiptkeyword.repository;

import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jp.co.translacat.domain.accountbook.receiptkeyword.entity.ReceiptKeyword;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static jp.co.translacat.domain.accountbook.receiptkeyword.entity.QReceiptKeyword.receiptKeyword;

@RequiredArgsConstructor
public class ReceiptKeywordRepositoryImpl implements ReceiptKeywordRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<ReceiptKeyword> findEffectiveKeywords(
            String currencyCode,
            String ocrLanguage
    ) {
        return queryFactory
                .selectFrom(receiptKeyword)
                .where(
                        receiptKeyword.enabled.isTrue(),
                        receiptKeyword.deleted.isFalse(),
                        receiptKeyword.ocrLanguage.eq(ocrLanguage),
                        receiptKeyword.currencyCode.isNull()
                                .or(receiptKeyword.currencyCode.eq(currencyCode))
                )
                .orderBy(
                        new CaseBuilder()
                                .when(receiptKeyword.currencyCode.eq(currencyCode))
                                .then(0)
                                .otherwise(1)
                                .asc(),
                        receiptKeyword.keywordType.asc(),
                        receiptKeyword.displayOrder.asc(),
                        receiptKeyword.id.asc()
                )
                .fetch();
    }
}