package jp.co.translacat.domain.languagelearning.listening.profile.service;

import jp.co.translacat.domain.languagelearning.listening.attempt.repository.ListeningItemAttemptRepository;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningProfileMetric;
import jp.co.translacat.domain.languagelearning.listening.outbox.service.ListeningOutboxTransactionService;
import jp.co.translacat.domain.languagelearning.listening.policy.ListeningProfilePolicy;
import jp.co.translacat.domain.languagelearning.listening.profile.entity.ListeningMetricHistory;
import jp.co.translacat.domain.languagelearning.listening.profile.repository.ListeningMetricHistoryRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;

@Service
@RequiredArgsConstructor
public class ListeningProfileRecalculationCommandService {

    private final ListeningItemAttemptRepository attemptRepository;
    private final ListeningMetricHistoryRepository historyRepository;
    private final ListeningProfilePolicy profilePolicy;
    private final ListeningOutboxTransactionService outboxTransactionService;

    @Transactional
    public void recalculate(
            ListeningOutboxTransactionService.ClaimedEvent event
    ) {
        var attempt = attemptRepository.findById(event.aggregateId())
                .orElseThrow();
        Long userId = attempt.getSession().getUser().getId();
        String language = attempt.getItem().getDailySet().getLearningLanguage();
        var all = historyRepository
                .findAllByUserIdAndLearningLanguageAndProfileAppliedTrueOrderByCreatedAtDesc(
                        userId,
                        language
                );

        for (ListeningProfileMetric metric : ListeningProfileMetric.values()) {
            int rank = 0;
            var activities = new HashSet<String>();

            for (ListeningMetricHistory history : all) {
                if (history.getMetricType() != metric) {
                    continue;
                }

                if (!activities.add(history.getReferenceActivityId())
                        || rank >= ListeningProfilePolicy.MAX_ACTIVITIES) {
                    history.updateRecency(0, 0);
                    continue;
                }

                rank++;
                double recency = profilePolicy.recencyWeight(rank);
                double finalWeight = profilePolicy.finalWeight(
                        rank,
                        history.getConfidence(),
                        history.getAssistanceLevel(),
                        history.getEvidenceWeight()
                );
                history.updateRecency(recency, finalWeight);
            }
        }

        outboxTransactionService.succeed(event.id(), LocalDateTime.now());
    }
}
