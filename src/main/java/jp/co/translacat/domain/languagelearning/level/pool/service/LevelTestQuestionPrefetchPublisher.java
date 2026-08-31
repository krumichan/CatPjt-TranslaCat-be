package jp.co.translacat.domain.languagelearning.level.pool.service;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestAnswerMode;
import jp.co.translacat.domain.languagelearning.level.entity.LevelTestItem;
import jp.co.translacat.domain.languagelearning.level.policy.LevelTestAdaptivePolicy;
import jp.co.translacat.domain.languagelearning.level.policy.LevelTestRecipe;
import jp.co.translacat.domain.languagelearning.level.pool.event.LevelTestQuestionPrefetchRequestedEvent;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LevelTestQuestionPrefetchPublisher {

    private final ApplicationEventPublisher eventPublisher;
    private final LevelTestAdaptivePolicy adaptivePolicy;

    public void publish(LevelTestItem currentItem) {
        int nextQuestionNumber = currentItem.getQuestionNumber() + 1;
        if (nextQuestionNumber > LevelTestRecipe.TOTAL_QUESTIONS) {
            return;
        }

        var candidateBands = currentItem.getAnswerMode()
                == LevelTestAnswerMode.CHOICE
                ? adaptivePolicy.objectiveCandidateBands(
                        currentItem.getComplexityBandValue()
                )
                : adaptivePolicy.candidateBands(
                        currentItem.getComplexityBandValue()
                );
        candidateBands.forEach(band -> eventPublisher.publishEvent(
                        new LevelTestQuestionPrefetchRequestedEvent(
                                currentItem.getSession().getId(),
                                nextQuestionNumber,
                                band
                        )
                ));
    }
}
