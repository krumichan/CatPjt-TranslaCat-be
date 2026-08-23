package jp.co.translacat.domain.languagelearning.listening.daily.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningItemStatus;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;

import static jp.co.translacat.domain.languagelearning.listening.daily.entity.QListeningItem.listeningItem;

@Repository
@RequiredArgsConstructor
public class ListeningItemRepositoryImpl
        implements ListeningItemRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public long countLogicalItemsByStatus(
            Long dailySetId,
            ListeningItemStatus status
    ) {
        Long count = queryFactory
                .select(listeningItem.itemIndex.countDistinct())
                .from(listeningItem)
                .where(
                        listeningItem.dailySet.id.eq(dailySetId),
                        listeningItem.status.eq(status)
                )
                .fetchOne();

        return count == null ? 0L : count;
    }
}
