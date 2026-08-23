package jp.co.translacat.domain.languagelearning.listening.evaluation.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;

import jp.co.translacat.domain.languagelearning.listening.evaluation.entity.ListeningTaskEvaluation;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

import static jp.co.translacat.domain.languagelearning.listening.evaluation.entity.QListeningTaskEvaluation.listeningTaskEvaluation;

@Repository
@RequiredArgsConstructor
public class ListeningTaskEvaluationRepositoryImpl
        implements ListeningTaskEvaluationRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<ListeningTaskEvaluation> findOfficialTrendSource(
            Long userId,
            String learningLanguage,
            LocalDateTime from,
            LocalDateTime to
    ) {
        return queryFactory
                .selectFrom(listeningTaskEvaluation)
                .where(
                        listeningTaskEvaluation.taskResponse.attempt.session.user.id
                                .eq(userId),
                        listeningTaskEvaluation.taskResponse.attempt.item.dailySet
                                .learningLanguage.eq(learningLanguage),
                        listeningTaskEvaluation.taskResponse.attempt.official.isTrue(),
                        listeningTaskEvaluation.taskResponse.attempt.answerRevealed
                                .isFalse(),
                        listeningTaskEvaluation.evaluable.isTrue(),
                        listeningTaskEvaluation.evaluatedAt.between(from, to)
                )
                .orderBy(listeningTaskEvaluation.evaluatedAt.asc())
                .fetch();
    }
}
