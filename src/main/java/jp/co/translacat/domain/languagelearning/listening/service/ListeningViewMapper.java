package jp.co.translacat.domain.languagelearning.listening.service;

import com.fasterxml.jackson.core.type.TypeReference;

import jp.co.translacat.domain.languagelearning.common.json.LanguageLearningJsonCodec;
import jp.co.translacat.domain.languagelearning.listening.attempt.entity.ListeningItemAttempt;
import jp.co.translacat.domain.languagelearning.listening.attempt.repository.ListeningItemAttemptRepository;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningItemStatus;
import jp.co.translacat.domain.languagelearning.listening.daily.service.ListeningDailySetQueryService;
import jp.co.translacat.domain.languagelearning.listening.dto.ListeningApiContract;
import jp.co.translacat.domain.languagelearning.listening.evaluation.entity.ListeningTaskEvaluation;
import jp.co.translacat.domain.languagelearning.listening.evaluation.repository.ListeningTaskEvaluationRepository;
import jp.co.translacat.domain.languagelearning.listening.response.entity.ListeningTaskResponse;
import jp.co.translacat.domain.languagelearning.listening.response.repository.ListeningTaskResponseRepository;
import jp.co.translacat.domain.languagelearning.listening.session.entity.ListeningSession;
import jp.co.translacat.domain.languagelearning.listening.setting.service.ListeningPolicySettingQueryService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ListeningViewMapper {

    private final ListeningItemAttemptRepository attemptRepository;
    private final ListeningTaskResponseRepository responseRepository;
    private final ListeningTaskEvaluationRepository evaluationRepository;
    private final ListeningPolicySettingQueryService policySettingService;
    private final LanguageLearningJsonCodec jsonCodec;
    private final ListeningDailySetQueryService dailySetQueryService;

    public ListeningApiContract.SessionView session(ListeningSession value) {
        int resumeHours = policySettingService.get().getResumeHours();
        List<ListeningItemAttempt> attempts = attemptRepository
                .findAllBySessionIdOrderByItemItemIndexAscAttemptNoAsc(value.getId());

        return new ListeningApiContract.SessionView(
                value.getId(),
                value.getDailySet().getId(),
                value.getStatus(),
                jsonCodec.read(
                        value.getSelectedTaskTypesJson(),
                        new TypeReference<List<ListeningTaskType>>() {
                        }
                ),
                value.getCompletedItemCount(),
                value.getEvaluatedItemCount(),
                value.getActualDurationMs(),
                value.getStartedAt(),
                value.getLastActivityAt(),
                value.getLastActivityAt().plus(Duration.ofHours(resumeHours)),
                attempts.stream().map(this::attempt).toList(),
                value.getDailySet().getStatus(),
                value.getDailySet().getTargetItemCount(),
                (int) attempts.stream().filter(ListeningItemAttempt::isOfficial)
                        .map(attempt -> attempt.getItem().getItemIndex()).distinct().count(),
                value.getDailySet().getFailureReason(),
                (int) dailySetQueryService.activeItems(value.getDailySet().getId()).stream()
                        .filter(item -> item.getStatus() == ListeningItemStatus.TTS_PENDING).count(),
                dailySetQueryService.generationInProgress(value.getDailySet().getId())
        );
    }

    public ListeningApiContract.HistoryDetailView history(
            ListeningSession value
    ) {
        return new ListeningApiContract.HistoryDetailView(
                session(value),
                attemptRepository
                        .findAllBySessionIdOrderByItemItemIndexAscAttemptNoAsc(
                                value.getId()
                        ).stream()
                        .map(attempt -> {
                            boolean reveal = attempt.isFinalized()
                                    || attempt.isAnswerRevealed();

                            return new ListeningApiContract.HistoryAttemptDetailView(
                                attempt.getItem().getId(),
                                attempt.getItem().getItemIndex(),
                                reveal ? attempt.getItem().getSourceText() : null,
                                reveal
                                        ? jsonCodec.read(
                                                attempt.getItem()
                                                        .getReferenceMeaningsJson(),
                                                new TypeReference<List<String>>() {
                                                }
                                        )
                                        : List.of(),
                                audioAvailability(
                                        attempt.getItem().getAudioObjectKey(),
                                        attempt.getItem().getAudioRetentionUntil(),
                                        attempt.getItem().getAudioDeletedAt(),
                                        LocalDateTime.now()
                                ),
                                attempt(attempt)
                            );
                        })
                        .toList()
        );
    }

    public ListeningApiContract.AttemptView attempt(
            ListeningItemAttempt value
    ) {
        return new ListeningApiContract.AttemptView(
                value.getId(),
                value.getItem().getId(),
                value.getAttemptNo(),
                value.getEvaluationPurpose(),
                value.getStatus(),
                value.isAnswerRevealed(),
                value.getContentOverallScore(),
                value.getListeningIndependenceScore(),
                value.getOverallScore(),
                new ListeningApiContract.PlaybackSummary(
                        value.getNormalPlaybackCount(),
                        value.getSlowPlaybackCount(),
                        value.getIndependencePolicyVersion()
                ),
                value.getEvaluatedTaskCount(),
                value.getCoverage(),
                value.getErrorCode(),
                responseRepository.findAllByAttemptIdOrderByTaskTypeAsc(
                        value.getId()
                ).stream().map(this::task).toList(),
                value.getItem().getItemIndex()
        );
    }

    public ListeningApiContract.TaskView task(ListeningTaskResponse value) {
        ListeningApiContract.AudioAvailabilityView audioAvailability =
                audioAvailability(
                        value.getUserAudioObjectKey(),
                        value.getAudioRetentionUntil(),
                        value.getAudioDeletedAt(),
                        LocalDateTime.now()
                );
        return new ListeningApiContract.TaskView(
                value.getId(),
                value.getTaskType(),
                value.getStatus(),
                value.getAnswerText(),
                audioAvailability.available(),
                value.getAudioDurationMs(),
                audioAvailability,
                value.getRerecordCount(),
                value.getAssistanceLevel(),
                jsonCodec.read(
                        value.getAssistanceUsageJson(),
                        new TypeReference<List<ListeningApiContract.AssistanceUsage>>() {
                        }
                ),
                value.getEvaluationErrorCode(),
                evaluationRepository
                        .findFirstByTaskResponseIdOrderByEvaluatedAtDesc(
                                value.getId()
                        ).map(this::evaluation).orElse(null)
        );
    }

    static ListeningApiContract.AudioAvailabilityView audioAvailability(
            String objectKey,
            LocalDateTime retentionUntil,
            LocalDateTime deletedAt,
            LocalDateTime now
    ) {
        boolean expired = deletedAt != null
                || (retentionUntil != null && retentionUntil.isBefore(now));
        boolean available = objectKey != null && !expired;
        return new ListeningApiContract.AudioAvailabilityView(
                available,
                expired,
                retentionUntil,
                deletedAt
        );
    }

    public ListeningApiContract.EvaluationView evaluation(
            ListeningTaskEvaluation value
    ) {
        return new ListeningApiContract.EvaluationView(
                value.getId(),
                value.getTaskType(),
                value.isEvaluable(),
                value.getScore(),
                value.getConfidence(),
                value.getReasonCode(),
                jsonCodec.read(
                        value.getMetricScoresJson(),
                        new TypeReference<List<Map<String, Object>>>() {
                        }
                ),
                jsonCodec.read(
                        value.getStrengthsJson(),
                        new TypeReference<List<String>>() {
                        }
                ),
                jsonCodec.read(
                        value.getImprovementsJson(),
                        new TypeReference<List<String>>() {
                        }
                ),
                jsonCodec.read(
                        value.getRecommendedAnswersJson(),
                        new TypeReference<List<String>>() {
                        }
                ),
                value.getEvaluatedAt()
        );
    }
}
