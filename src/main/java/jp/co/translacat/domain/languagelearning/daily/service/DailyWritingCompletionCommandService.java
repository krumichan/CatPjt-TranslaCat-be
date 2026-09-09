package jp.co.translacat.domain.languagelearning.daily.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

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
import org.springframework.transaction.annotation.Propagation;

@Service
@RequiredArgsConstructor
public class DailyWritingCompletionCommandService {

    private final DailyWritingSetRepository dailySetRepository;
    private final DailyWritingItemRepository itemRepository;
    private final WritingAnswerRepository answerRepository;
    private final WritingEvaluationRepository evaluationRepository;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DailyWritingSet completeIfAllEvaluated(Long dailySetId) {
        DailyWritingSet dailySet = dailySetRepository.findLockedById(dailySetId).orElse(null);
        if (dailySet == null) {
            return null;
        }
        // A web request may already hold this entity in its open persistence context.
        // Refresh the locked row before reconciling or returning its current status.
        entityManager.refresh(dailySet, LockModeType.PESSIMISTIC_WRITE);
        if (dailySet.getStatus() == DailySetStatus.READY && allItemsEvaluated(dailySet)) {
            dailySet.complete();
        }
        return dailySet;
    }

    private boolean allItemsEvaluated(DailyWritingSet dailySet) {
        var items = itemRepository.findAllByDailySetIdOrderByOrderNoAsc(dailySet.getId());
        if (items.size() != dailySet.getSentenceCount() || items.isEmpty()) {
            return false;
        }
        for (int index = 0; index < items.size(); index++) {
            DailyWritingItem item = items.get(index);
            if (item.getOrderNo() != index + 1) {
                return false;
            }
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
