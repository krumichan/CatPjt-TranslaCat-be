package jp.co.translacat.domain.languagelearning.level.service;

import jp.co.translacat.domain.languagelearning.daily.entity.WritingEvaluation;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestSession;
import jp.co.translacat.domain.languagelearning.level.policy.LevelBaseScorePolicy;
import jp.co.translacat.domain.languagelearning.profile.service.LearningProfileCommandService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LevelTestCompletionCommandService {

    private final LevelTestQueryService levelTestQueryService;
    private final LevelBaseScorePolicy baseScorePolicy;
    private final LearningProfileCommandService learningProfileCommandService;

    @Transactional
    public double complete(LevelTestSession session) {
        List<WritingEvaluation> evaluations =
                levelTestQueryService.getSuccessfulEvaluations(
                        session.getId()
                );
        double baseLevelScore = baseScorePolicy.calculate(
                evaluations,
                session.getTotalQuestions()
        );

        session.complete(baseLevelScore);
        learningProfileCommandService.completeLevelTest(
                session.getUser().getId(),
                baseLevelScore
        );

        return baseLevelScore;
    }
}
