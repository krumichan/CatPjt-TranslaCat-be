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
import jp.co.translacat.domain.languagelearning.listening.profile.repository.ListeningMetricHistoryRepository;
import jp.co.translacat.domain.languagelearning.listening.response.entity.ListeningTaskResponse;
import jp.co.translacat.domain.languagelearning.listening.response.repository.ListeningTaskResponseRepository;

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
    private final ListeningTaskEvaluationRepository evaluationRepository;
    private final ListeningMetricHistoryRepository historyRepository;
    private final ListeningOutboxCommandService outboxCommandService;
    private final ListeningProgressPolicy progressPolicy;
    private final LearningActivityCommandService activityCommandService;

    @Transactional
    public boolean finalizeIfTerminal(Long attemptId) {
        ListeningItemAttempt attempt = attemptRepository.findLockedById(attemptId)
                .orElseThrow();

        if (attempt.isFinalized()) {
            return true;
        }

        List<ListeningTaskResponse> selected = responseRepository
                .findAllByAttemptIdOrderByTaskTypeAsc(attemptId)
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
                .findAllByTaskResponseAttemptIdOrderByTaskTypeAsc(attemptId);
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

        if (progressPolicy.eligible(
                attempt.isOfficial(),
                attempt.isPractice(),
                attempt.isAnswerRevealed(),
                attempt.isProgressApplied(),
                selected.stream().map(ListeningTaskResponse::getStatus).toList()
        )) {
            attempt.getSession().recordLearning(
                    !evaluated.isEmpty(),
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

        if (profileApplied) {
            outboxCommandService.enqueue(
                    ListeningOutboxType.RECALCULATE_PROFILE,
                    attempt.getId(),
                    null,
                    "listening:attempt:" + attempt.getId() + ":profile"
            );
        }

        return true;
    }

}
