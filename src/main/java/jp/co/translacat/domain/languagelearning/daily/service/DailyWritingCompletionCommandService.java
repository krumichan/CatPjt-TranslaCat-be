package jp.co.translacat.domain.languagelearning.daily.service;

import jp.co.translacat.domain.languagelearning.common.enums.DailySetStatus;
import jp.co.translacat.domain.languagelearning.common.enums.EvaluationStatus;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingItem;
import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingSet;
import jp.co.translacat.domain.languagelearning.daily.entity.WritingEvaluation;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingItemRepository;
import jp.co.translacat.domain.languagelearning.daily.repository.DailyWritingSetRepository;
import jp.co.translacat.domain.languagelearning.daily.repository.WritingAnswerRepository;
import jp.co.translacat.domain.languagelearning.daily.repository.WritingEvaluationRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DailyWritingCompletionCommandService {

    private final DailyWritingSetRepository dailySetRepository;
    private final DailyWritingItemRepository itemRepository;
    private final WritingAnswerRepository answerRepository;
    private final WritingEvaluationRepository evaluationRepository;

    @Transactional
    public void completeIfAllEvaluated(Long dailySetId) {
        if (!allItemsEvaluated(dailySetId)) {
            return;
        }

        dailySetRepository.findById(dailySetId)
                .filter(dailySet -> dailySet.getStatus()
                        != DailySetStatus.COMPLETED)
                .ifPresent(DailyWritingSet::complete);
    }

    private boolean allItemsEvaluated(Long dailySetId) {
        for (DailyWritingItem item :
                itemRepository.findAllByDailySetIdOrderByOrderNoAsc(
                        dailySetId
                )) {
            if (!hasSuccessfulEvaluation(item)) {
                return false;
            }
        }

        return true;
    }

    private boolean hasSuccessfulEvaluation(DailyWritingItem item) {
        return answerRepository
                .findAllByDailyItemIdOrderByAttemptDateAsc(item.getId())
                .stream()
                .map(answer -> evaluationRepository
                        .findByAnswerId(answer.getId())
                        .orElse(null)
                )
                .anyMatch(this::isSuccessful);
    }

    private boolean isSuccessful(WritingEvaluation evaluation) {
        return evaluation != null
                && evaluation.getStatus() == EvaluationStatus.SUCCESS;
    }
}
