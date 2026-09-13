package jp.co.translacat.domain.languagelearning.practice.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jp.co.translacat.domain.languagelearning.practice.entity.PracticeSet;
import jp.co.translacat.domain.languagelearning.practice.enums.PracticeGenerationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

import static jp.co.translacat.domain.languagelearning.practice.entity.QPracticeSet.practiceSet;

@Repository
@RequiredArgsConstructor
public class PracticeSetRepositoryImpl implements PracticeSetRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    @Override
    public List<PracticeSet> findDueByGenerationStatus(
            PracticeGenerationStatus status,
            LocalDateTime now,
            int limit
    ) {
        if (limit <= 0) {
            return List.of();
        }
        return queryFactory
                .selectFrom(practiceSet)
                .where(
                        practiceSet.generationStatus.eq(status),
                        practiceSet.generationAvailableAt.isNull()
                                .or(practiceSet.generationAvailableAt.loe(now))
                )
                .orderBy(practiceSet.id.asc())
                .limit(limit)
                .fetch();
    }
}
