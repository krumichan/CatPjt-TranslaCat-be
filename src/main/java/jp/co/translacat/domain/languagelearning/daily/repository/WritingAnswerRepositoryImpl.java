package jp.co.translacat.domain.languagelearning.daily.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.RequiredArgsConstructor;

import static jp.co.translacat.domain.languagelearning.daily.entity.QWritingAnswer.writingAnswer;

@RequiredArgsConstructor
public class WritingAnswerRepositoryImpl implements WritingAnswerRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public long countDistinctAnsweredItems(Long userId) {
        Long count = queryFactory
                .select(writingAnswer.dailyItem.id.countDistinct())
                .from(writingAnswer)
                .where(writingAnswer.user.id.eq(userId))
                .fetchOne();

        return count == null ? 0L : count;
    }
}
