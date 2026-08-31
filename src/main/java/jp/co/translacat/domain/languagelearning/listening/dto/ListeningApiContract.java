package jp.co.translacat.domain.languagelearning.listening.dto;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningAssistanceLevel;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningAssistanceType;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningAttemptStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningDailySetStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningDifficulty;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningEvaluationPurpose;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningItemStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningProfileMetric;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningPlaybackType;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningRecommendationStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningSessionStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskStatus;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningWeaknessState;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ListeningApiContract {

    private ListeningApiContract() {
    }

    public record DailySetCreateRequest(
            Integer itemCount,
            ListeningDifficulty difficulty,
            String idempotencyKey
    ) {
    }

    public record DailySetView(
            Long dailySetId,
            LocalDate learningDate,
            String originLanguage,
            String learningLanguage,
            ListeningDifficulty difficulty,
            ListeningDailySetStatus status,
            int targetItemCount,
            int physicalItemCount,
            int readyItemCount,
            int completedItemCount,
            String failureReason,
            List<ItemSummary> items
    ) {
    }

    public record ItemSummary(
            Long itemId,
            int itemIndex,
            int replacementSequence,
            ListeningItemStatus status,
            boolean playable,
            Integer audioDurationMs
    ) {
    }

    public record SessionCreateRequest(
            Long dailySetId,
            List<ListeningTaskType> selectedTaskTypes,
            String idempotencyKey
    ) {
    }

    public record ActiveSessionView(
            boolean active,
            SessionView session
    ) {
    }

    public record SessionView(
            Long sessionId,
            Long dailySetId,
            ListeningSessionStatus status,
            List<ListeningTaskType> selectedTaskTypes,
            int completedItemCount,
            int evaluatedItemCount,
            long actualDurationMs,
            LocalDateTime startedAt,
            LocalDateTime lastActivityAt,
            LocalDateTime resumableUntil,
            List<AttemptView> attempts
    ) {
    }

    public record ItemView(
            Long sessionId,
            Long itemId,
            int itemIndex,
            ListeningItemStatus status,
            boolean playable,
            String referenceAudioPath,
            Integer audioDurationMs,
            String topicHint,
            List<String> keywordHints,
            String sourceText,
            List<String> referenceMeanings,
            AttemptView attempt
    ) {
    }

    public record AttemptView(
            Long attemptId,
            Long itemId,
            int attemptNo,
            ListeningEvaluationPurpose evaluationPurpose,
            ListeningAttemptStatus status,
            boolean answerRevealed,
            Double contentOverallScore,
            Double listeningIndependenceScore,
            Double overallScore,
            PlaybackSummary playbackSummary,
            int evaluatedTaskCount,
            double coverage,
            String errorCode,
            List<TaskView> tasks
    ) {
    }

    public record PlaybackRequest(
            Long attemptId,
            ListeningPlaybackType playbackType,
            String clientEventId
    ) {
    }

    public record PlaybackSummary(
            int normalPlaybackCount,
            int slowPlaybackCount,
            String policyVersion
    ) {
    }

    public record TaskView(
            Long taskResponseId,
            ListeningTaskType taskType,
            ListeningTaskStatus status,
            String answerText,
            boolean audioUploaded,
            Integer audioDurationMs,
            AudioAvailabilityView audioAvailability,
            int rerecordCount,
            ListeningAssistanceLevel assistanceLevel,
            List<AssistanceUsage> assistanceUsage,
            String evaluationErrorCode,
            EvaluationView evaluation
    ) {
    }

    public record AssistanceUsage(
            ListeningAssistanceType type,
            int count
    ) {
    }

    public record ResponseUpsertRequest(
            String answer,
            List<AssistanceUsage> assistanceUsage,
            String idempotencyKey
    ) {
    }

    public record AudioUploadView(
            Long taskResponseId,
            int durationMs,
            int rerecordCount,
            LocalDateTime retentionUntil
    ) {
    }

    public record SubmitRequest(
            String idempotencyKey,
            long actualDurationMs
    ) {
    }

    public record RetryRequest(
            ListeningTaskType taskType,
            String idempotencyKey
    ) {
    }

    public record PracticeAttemptRequest(
            String idempotencyKey,
            List<ListeningTaskType> selectedTaskTypes
    ) {
    }

    public record SkipRequest(
            String idempotencyKey,
            long actualDurationMs
    ) {
    }

    public record RevealAnswerView(
            Long attemptId,
            String sourceText,
            List<String> referenceMeanings,
            boolean excludedFromProgress,
            boolean excludedFromProfile
    ) {
    }

    public record EvaluationView(
            Long evaluationId,
            ListeningTaskType taskType,
            boolean evaluable,
            Double score,
            Double confidence,
            String reasonCode,
            List<Map<String, Object>> metrics,
            List<String> strengths,
            List<String> improvements,
            List<String> recommendedAnswers,
            LocalDateTime evaluatedAt
    ) {
    }

    public record EvaluationReportRequest(
            String reasonCode,
            String comment,
            boolean consentToRetainAudio,
            String idempotencyKey
    ) {
    }

    public record EvaluationReportView(
            Long reportId,
            Long taskResponseId,
            String status,
            boolean consentToRetainAudio,
            LocalDateTime audioRetentionUntil
    ) {
    }

    public record SessionCompleteRequest(long actualDurationMs) {
    }

    public record SessionResultView(
            Long sessionId,
            ListeningSessionStatus status,
            int learnedItemCount,
            int evaluatedItemCount,
            Double averageScore,
            double coverage,
            List<AttemptView> attempts
    ) {
    }

    public record DashboardView(
            String learningLanguage,
            LocalDate from,
            LocalDate to,
            List<MetricProfileView> metrics,
            List<TaskTrendView> taskTrends,
            List<RecommendationView> recommendations
    ) {
    }

    public record MetricProfileView(
            ListeningProfileMetric metric,
            Double score,
            int sampleCount,
            String confidence,
            ListeningWeaknessState weaknessState,
            boolean growthActive,
            Double growthDelta
    ) {
    }

    public record TaskTrendView(
            ListeningTaskType taskType,
            LocalDate date,
            Double averageScore,
            int sampleCount
    ) {
    }

    public record MetricTrendView(
            ListeningTaskType taskType,
            String metric,
            LocalDate date,
            Double averageScore,
            int sampleCount
    ) {
    }

    public record RecommendationView(
            Long recommendationId,
            ListeningProfileMetric targetMetric,
            String recommendedActivity,
            String recommendedTask,
            String reason,
            String ctaLabel,
            int priority,
            ListeningRecommendationStatus status,
            LocalDateTime expiresAt
    ) {
    }

    public record HistoryItemView(
            Long sessionId,
            Long attemptId,
            Long itemId,
            LocalDate learningDate,
            ListeningEvaluationPurpose purpose,
            ListeningAttemptStatus status,
            Double score,
            double coverage,
            List<ListeningTaskType> taskTypes
    ) {
    }

    public record HistoryDetailView(
            SessionView session,
            List<HistoryAttemptDetailView> attempts
    ) {
    }

    public record HistoryAttemptDetailView(
            Long itemId,
            int itemIndex,
            String sourceText,
            List<String> referenceMeanings,
            AudioAvailabilityView referenceAudio,
            AttemptView attempt
    ) {
    }

    public record AudioAvailabilityView(
            boolean available,
            boolean expired,
            LocalDateTime retentionUntil,
            LocalDateTime deletedAt
    ) {
    }

    public record PolicyView(
            boolean enabled,
            int defaultItemCount,
            int minItemCount,
            int maxItemCount,
            int hardItemLimit,
            int resumeHours,
            int referenceAudioRetentionDays,
            int userAudioRetentionDays,
            int reportedAudioRetentionDays,
            int automaticRetryLimit,
            int manualRetryLimit,
            int practiceAttemptLimit,
            String profilePolicyVersion,
            String modelConfigVersion,
            boolean referenceTtsRegenerationEnabled
    ) {
    }
}
