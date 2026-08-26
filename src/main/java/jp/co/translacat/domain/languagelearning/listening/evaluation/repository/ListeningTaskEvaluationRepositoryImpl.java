package jp.co.translacat.domain.languagelearning.listening.evaluation.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;

import jp.co.translacat.domain.languagelearning.listening.attempt.entity.QListeningItemAttempt;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.QListeningDailySet;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.QListeningItem;
import jp.co.translacat.domain.languagelearning.listening.evaluation.entity.ListeningTaskEvaluation;
import jp.co.translacat.domain.languagelearning.listening.response.entity.QListeningTaskResponse;
import jp.co.translacat.domain.languagelearning.listening.session.entity.QListeningSession;

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
        QListeningTaskResponse taskResponse =
                new QListeningTaskResponse("taskResponse");
        QListeningItemAttempt attempt =
                new QListeningItemAttempt("attempt");
        QListeningSession session = new QListeningSession("session");
        QListeningItem item = new QListeningItem("item");
        QListeningDailySet dailySet = new QListeningDailySet("dailySet");

        return queryFactory
                .selectFrom(listeningTaskEvaluation)
                .join(listeningTaskEvaluation.taskResponse, taskResponse)
                .join(taskResponse.attempt, attempt)
                .join(attempt.session, session)
                .join(attempt.item, item)
                .join(item.dailySet, dailySet)
                .where(
                        session.user.id.eq(userId),
                        dailySet.learningLanguage.eq(learningLanguage),
                        attempt.official.isTrue(),
                        attempt.answerRevealed.isFalse(),
                        listeningTaskEvaluation.evaluable.isTrue(),
                        listeningTaskEvaluation.evaluatedAt.between(from, to)
                )
                .orderBy(listeningTaskEvaluation.evaluatedAt.asc())
                .fetch();
    }
}
