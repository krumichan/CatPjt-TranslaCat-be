package jp.co.translacat.domain.languagelearning.listening.evaluation.service;

import jp.co.translacat.domain.languagelearning.listening.attempt.entity.ListeningItemAttempt;
import jp.co.translacat.domain.languagelearning.listening.attempt.repository.ListeningItemAttemptRepository;
import jp.co.translacat.domain.languagelearning.activity.service.LearningActivityCommandService;
import jp.co.translacat.domain.languagelearning.common.enums.LearningSource;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningOutboxType;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskStatus;
import jp.co.translacat.domain.languagelearning.listening.evaluation.entity.ListeningTaskEvaluation;
import jp.co.translacat.domain.languagelearning.listening.evaluation.repository.ListeningTaskEvaluationRepository;
import jp.co.translacat.domain.languagelearning.listening.outbox.service.ListeningOutboxCommandService;
import jp.co.translacat.domain.languagelearning.listening.policy.ListeningProgressPolicy;
import jp.co.translacat.domain.languagelearning.listening.policy.ListeningIndependencePolicy;
import jp.co.translacat.domain.languagelearning.listening.playback.repository.ListeningPlaybackEventRepository;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningPlaybackType;
import jp.co.translacat.domain.languagelearning.listening.profile.repository.ListeningMetricHistoryRepository;
import jp.co.translacat.domain.languagelearning.listening.profile.entity.ListeningMetricHistory;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningProfileMetric;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningAssistanceLevel;
import jp.co.translacat.domain.languagelearning.listening.policy.ListeningProfilePolicy;
import jp.co.translacat.domain.languagelearning.listening.response.entity.ListeningTaskResponse;
import jp.co.translacat.domain.languagelearning.listening.response.repository.ListeningTaskResponseRepository;
import jp.co.translacat.domain.languagelearning.listening.session.entity.ListeningSession;
import jp.co.translacat.domain.languagelearning.listening.session.service.ListeningSessionLockService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ListeningAttemptFinalizationCommandService {

    private static final Set<ListeningTaskStatus> TERMINAL = Set.of(
            ListeningTaskStatus.EVALUATED,
            ListeningTaskStatus.EVALUATION_FAILED,
            ListeningTaskStatus.NOT_EVALUABLE,
            ListeningTaskStatus.SKIPPED
    );

    private final ListeningItemAttemptRepository attemptRepository;
    private final ListeningTaskResponseRepository responseRepository;
    private final ListeningSessionLockService lockService;
    private final ListeningTaskEvaluationRepository evaluationRepository;
    private final ListeningMetricHistoryRepository historyRepository;
    private final ListeningOutboxCommandService outboxCommandService;
    private final ListeningProgressPolicy progressPolicy;
    private final ListeningIndependencePolicy independencePolicy;
    private final ListeningPlaybackEventRepository playbackEventRepository;
    private final ListeningProfilePolicy profilePolicy;
    private final LearningActivityCommandService activityCommandService;

    @Transactional
    public boolean finalizeIfTerminal(Long attemptId) {
        ListeningItemAttempt attempt = lockService.attempt(attemptId);
        ListeningSession session = attempt.getSession();

        if (attempt.isFinalized()) {
            return true;
        }

        List<ListeningTaskResponse> selected = responseRepository
                .findAllLockedByAttemptIdOrderByTaskTypeAsc(attemptId)
                .stream()
                .filter(value -> value.getStatus()
                        != ListeningTaskStatus.NOT_SELECTED)
                .toList();

        if (selected.isEmpty()
                || selected.stream().anyMatch(value -> !TERMINAL.contains(
                        value.getStatus()
                ))) {
            return false;
        }

        List<ListeningTaskEvaluation> evaluations = evaluationRepository
                .findAllLockedByTaskResponseAttemptIdOrderByTaskTypeAsc(attemptId);
        List<ListeningTaskEvaluation> evaluated = evaluations.stream()
                .filter(ListeningTaskEvaluation::isEvaluable)
                .filter(value -> value.getScore() != null)
                .toList();
        Double average = evaluated.isEmpty()
                ? null
                : evaluated.stream()
                        .mapToDouble(ListeningTaskEvaluation::getScore)
                        .average()
                        .orElse(0);
        String evaluationVersion = evaluations.stream()
                .map(ListeningTaskEvaluation::getEvaluationVersion)
                .filter(value -> value != null && !value.isBlank())
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse("not-evaluable");
        boolean profileApplied = historyRepository
                .existsByReferenceActivityIdAndProfileAppliedTrue(
                        "LISTENING_ATTEMPT:" + attempt.getId()
                );
        LocalDateTime now = LocalDateTime.now();
        attempt.finish(
                average,
                evaluated.size(),
                selected.size(),
                evaluationVersion,
                profileApplied,
                now
        );

        if (average != null && attempt.getSubmittedAt() != null) {
            long normalCount = playbackEventRepository
                    .countByAttemptIdAndPlaybackTypeAndOccurredAtLessThanEqual(
                            attemptId, ListeningPlaybackType.NORMAL, attempt.getSubmittedAt());
            long slowCount = playbackEventRepository
                    .countByAttemptIdAndPlaybackTypeAndOccurredAtLessThanEqual(
                            attemptId, ListeningPlaybackType.SLOW, attempt.getSubmittedAt());
            int independence = independencePolicy.score(normalCount, slowCount);
            double adjusted = independencePolicy.adjustedOverall(average, independence);
            attempt.applyListeningIndependence(
                    average,
                    (double) independence,
                    adjusted,
                    Math.toIntExact(Math.min(normalCount, Integer.MAX_VALUE)),
                    Math.toIntExact(Math.min(slowCount, Integer.MAX_VALUE)),
                    ListeningIndependencePolicy.VERSION
            );
        }

        boolean progressEligible = progressPolicy.eligible(
                attempt.isOfficial(),
                attempt.isPractice(),
                attempt.isAnswerRevealed(),
                attempt.isProgressApplied(),
                selected.stream().map(ListeningTaskResponse::getStatus).toList()
        );
        if (progressEligible) {
            session.recordLearning(
                    evaluated.size() == selected.size(),
                    attempt.getActualDurationMs(),
                    now
            );
            attempt.getItem().getDailySet().registerCompletedLearning();
            activityCommandService.getOrCreate(
                    attempt.getSession().getUser().getId(),
                    LearningSource.LISTENING,
                    String.valueOf(attempt.getId()),
                    attempt.getItem().getDailySet().getLearningDate(),
                    "Daily Listening",
                    attempt.getActualDurationMs() / 1000,
                    attempt.getStartedAt(),
                    now
            );
            attempt.markProgressApplied();
        }

        boolean independenceProfileApplied = false;
        if (progressEligible && attempt.getListeningIndependenceScore() != null) {
            String referenceEvaluationId = "LISTENING_ATTEMPT:" + attempt.getId() + ":INDEPENDENCE";
            if (!historyRepository.existsByReferenceEvaluationIdAndMetricType(
                    referenceEvaluationId, ListeningProfileMetric.LISTENING_INDEPENDENCE)) {
                int rank = Math.min(
                        ListeningProfilePolicy.MAX_ACTIVITIES,
                        historyRepository.findTop30ByUserIdAndLearningLanguageAndMetricTypeAndProfileAppliedTrueOrderByCreatedAtDesc(
                                attempt.getSession().getUser().getId(),
                                attempt.getItem().getDailySet().getLearningLanguage(),
                                ListeningProfileMetric.LISTENING_INDEPENDENCE
                        ).size() + 1
                );
                double recency = profilePolicy.recencyWeight(rank);
                double finalWeight = profilePolicy.finalWeight(rank, 1.0, ListeningAssistanceLevel.INDEPENDENT, 1.0);
                historyRepository.save(ListeningMetricHistory.create(
                        attempt.getSession().getUser(),
                        attempt.getItem().getDailySet().getLearningLanguage(),
                        null,
                        ListeningProfileMetric.LISTENING_INDEPENDENCE,
                        attempt.getListeningIndependenceScore(),
                        1.0, recency, 1.0, 1.0, finalWeight,
                        ListeningAssistanceLevel.INDEPENDENT,
                        "LISTENING_ATTEMPT:" + attempt.getId(),
                        referenceEvaluationId,
                        true, false, true,
                        ListeningIndependencePolicy.VERSION,
                        ListeningProfilePolicy.VERSION
                ));
            }
            independenceProfileApplied = true;
        }

        if (profileApplied || independenceProfileApplied) {
            outboxCommandService.enqueue(
                    ListeningOutboxType.RECALCULATE_PROFILE,
                    attempt.getId(),
                    null,
                    "listening:attempt:" + attempt.getId() + ":profile"
            );
        }

        if (attempt.isOfficial()) {
            List<ListeningItemAttempt> official = attemptRepository
                    .findAllLockedBySessionIdOrderByItemItemIndexAscAttemptNoAsc(
                            session.getId()
                    ).stream()
                    .filter(ListeningItemAttempt::isOfficial)
                    .toList();
            if (session.hasAllTargetItems(official.stream()
                    .map(value -> value.getItem().getItemIndex()).toList())
                    && official.stream().allMatch(ListeningItemAttempt::isFinalized)
                    && (session.isActive() || session.getStatus()
                        == jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningSessionStatus.EVALUATING)) {
                session.complete(now);
            }
        }

        return true;
    }

}
