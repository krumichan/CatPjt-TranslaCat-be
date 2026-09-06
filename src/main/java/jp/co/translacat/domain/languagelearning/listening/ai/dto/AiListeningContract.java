package jp.co.translacat.domain.languagelearning.listening.ai.dto;

import jp.co.translacat.domain.languagelearning.common.enums.KeywordSource;
import jp.co.translacat.domain.languagelearning.common.enums.KeywordType;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningAssistanceType;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningDifficulty;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningEvaluationPurpose;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningLearningMode;
import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningTaskType;
import jp.co.translacat.domain.languagelearning.quality.dto.DiversityContext;
import jp.co.translacat.domain.languagelearning.quality.dto.DiversityMetadata;
import jp.co.translacat.domain.languagelearning.quality.dto.DiversitySummary;
import jp.co.translacat.domain.languagelearning.quality.dto.LanguageComplexityContext;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class AiListeningContract {

    private AiListeningContract() {
    }

    public record Keyword(
            String key,
            String text,
            KeywordSource source,
            KeywordType type,
            String canonicalKey,
            Double selectionWeight
    ) {
    }

    public record Topic(String id, String title) {
    }

    public record UserContext(
            String originLanguage,
            String learningLanguage,
            ListeningDifficulty level,
            List<String> profileFocus
    ) {
    }

    public record SetContext(
            LocalDate learningDate,
            ListeningLearningMode learningMode,
            Topic topic,
            List<Keyword> selectedKeywords,
            int itemCount,
            ListeningDifficulty difficulty
    ) {
    }

    public record GenerationConstraints(
            Double audioSecondsMin,
            Double audioSecondsMax,
            List<String> recentContentHashes,
            List<String> recentSimilaritySummaries
    ) {
    }

    public record GenerationRequest(
            String requestId,
            String idempotencyKey,
            UserContext userContext,
            SetContext setContext,
            GenerationConstraints constraints,
            String policyVersion,
            String modelConfigVersion,
            int manualRetryAttempt,
            LanguageComplexityContext languageComplexity,
            DiversityContext diversityContext,
            String contentDiversityPolicyVersion
    ) {
        public GenerationRequest(
                String requestId, String idempotencyKey, UserContext userContext,
                SetContext setContext, GenerationConstraints constraints,
                String policyVersion, String modelConfigVersion, int manualRetryAttempt
        ) {
            this(requestId, idempotencyKey, userContext, setContext, constraints,
                    policyVersion, modelConfigVersion, manualRetryAttempt,
                    null, DiversityContext.empty(), null);
        }
    }

    public record Safety(boolean passed, List<String> categories) {
    }

    public record ChoiceOption(String key, String text) {
    }

    public record GeneratedItem(
            int itemIndex,
            String sourceText,
            String normalizedSourceText,
            List<String> referenceMeanings,
            List<String> keyMeaningUnits,
            List<String> targetKeywords,
            double estimatedAudioSeconds,
            String contentHash,
            String similarityKey,
            Safety safety,
            Integer languageComplexityBand,
            DiversityMetadata diversityMetadata,
            String question,
            List<ChoiceOption> options,
            String correctOptionKey,
            String comprehensionFocus,
            List<String> summaryKeyPoints
    ) {
        public GeneratedItem(
                int itemIndex, String sourceText, String normalizedSourceText,
                List<String> referenceMeanings, List<String> keyMeaningUnits,
                List<String> targetKeywords, double estimatedAudioSeconds,
                String contentHash, String similarityKey, Safety safety
        ) {
            this(itemIndex, sourceText, normalizedSourceText, referenceMeanings,
                    keyMeaningUnits, targetKeywords, estimatedAudioSeconds, contentHash,
                    similarityKey, safety, null, null, null, List.of(), null, null, List.of());
        }
    }

    public record GenerationResponse(
            String requestId,
            String generationVersion,
            String policyVersion,
            String modelConfigVersion,
            List<GeneratedItem> items,
            Map<String, Object> usage,
            String contentDiversityPolicyVersion,
            String languageComplexityPolicyVersion,
            DiversitySummary diversitySummary
    ) {
        public GenerationResponse(
                String requestId, String generationVersion, String policyVersion,
                String modelConfigVersion, List<GeneratedItem> items, Map<String, Object> usage
        ) {
            this(requestId, generationVersion, policyVersion, modelConfigVersion,
                    items, usage, null, null, null);
        }
    }

    public record Voice(
            String locale,
            String voiceKey,
            String version,
            String accent
    ) {
    }

    public record TtsRequest(
            String requestId,
            String idempotencyKey,
            Long itemId,
            String sourceText,
            String contentHash,
            String generationVersion,
            String learningLanguage,
            Voice voice,
            String playbackSpeed,
            String policyVersion,
            String modelConfigVersion,
            int automaticRetryLimit,
            int manualRetryAttempt
    ) {
    }

    public record Audio(
            String audioReference,
            int durationMs,
            String format,
            int sampleRate,
            int channels,
            Voice voice,
            String textHash,
            String checksum,
            String cacheKey,
            String ttsVersion
    ) {
    }

    public record AiError(
            String code,
            String failedStage,
            String message,
            boolean retryable,
            Map<String, Object> details
    ) {
    }

    public record TtsResponse(
            String requestId,
            Long itemId,
            String status,
            String sourceText,
            String contentHash,
            String generationVersion,
            Audio audio,
            AiError error,
            Map<String, Object> usage
    ) {
    }

    public record AssistanceUsage(
            ListeningAssistanceType type,
            int count
    ) {
    }

    public record DictationRequest(
            String requestId,
            String idempotencyKey,
            Long itemId,
            Long attemptId,
            ListeningEvaluationPurpose evaluationPurpose,
            boolean answerRevealed,
            List<AssistanceUsage> assistanceUsage,
            String policyVersion,
            String modelConfigVersion,
            int manualRetryAttempt,
            String sourceText,
            String answer,
            String learningLanguage,
            Map<String, List<String>> acceptedVariants
    ) {
    }

    public record InterpretationRequest(
            String requestId,
            String idempotencyKey,
            Long itemId,
            Long attemptId,
            ListeningEvaluationPurpose evaluationPurpose,
            boolean answerRevealed,
            List<AssistanceUsage> assistanceUsage,
            String policyVersion,
            String modelConfigVersion,
            int manualRetryAttempt,
            String sourceText,
            List<String> referenceMeanings,
            List<String> keyMeaningUnits,
            String answer,
            String originLanguage,
            String learningLanguage
    ) {
    }

    public record ComprehensionRequest(
            String requestId,
            String idempotencyKey,
            Long itemId,
            Long attemptId,
            ListeningEvaluationPurpose evaluationPurpose,
            boolean answerRevealed,
            List<AssistanceUsage> assistanceUsage,
            String policyVersion,
            String modelConfigVersion,
            int manualRetryAttempt,
            String question,
            List<ChoiceOption> options,
            String selectedOptionKey,
            String correctOptionKey,
            String comprehensionFocus,
            String originLanguage,
            String learningLanguage
    ) {
    }

    public record SummaryRequest(
            String requestId,
            String idempotencyKey,
            Long itemId,
            Long attemptId,
            ListeningEvaluationPurpose evaluationPurpose,
            boolean answerRevealed,
            List<AssistanceUsage> assistanceUsage,
            String policyVersion,
            String modelConfigVersion,
            int manualRetryAttempt,
            String sourceText,
            List<String> summaryKeyPoints,
            String answer,
            String originLanguage,
            String learningLanguage
    ) {
    }

    public record RepeatRequest(
            String requestId,
            String idempotencyKey,
            Long itemId,
            Long attemptId,
            ListeningEvaluationPurpose evaluationPurpose,
            boolean answerRevealed,
            List<AssistanceUsage> assistanceUsage,
            String policyVersion,
            String modelConfigVersion,
            int manualRetryAttempt,
            String sourceText,
            double sourceDurationSeconds,
            String learningLanguage,
            List<String> phraseHints
    ) {
    }

    public record MetricEvidence(
            Integer startMs,
            Integer endMs,
            String reference,
            String recognized,
            String metric,
            String severity,
            String feedback
    ) {
    }

    public record Alignment(
            String source,
            String answer,
            String status,
            Integer sourceIndex,
            Integer answerIndex,
            Integer startMs,
            Integer endMs
    ) {
    }

    public record Metric(
            String type,
            String state,
            Double score,
            double weight,
            double confidence,
            List<MetricEvidence> evidence,
            String notEvaluableReason
    ) {
    }

    public record ProfileSignal(
            String metric,
            double score,
            double confidence,
            double evidenceWeight,
            List<String> evidenceIds,
            ListeningTaskType sourceTask,
            String policyVersion
    ) {
    }

    public record TaskResult(
            ListeningTaskType taskType,
            String status,
            boolean evaluable,
            Double score,
            Double confidence,
            String reasonCode,
            String assistanceLevel,
            List<Metric> metrics,
            List<Alignment> alignment,
            List<MetricEvidence> evidence,
            List<String> strengths,
            List<String> improvements,
            List<String> recommendedInterpretations,
            List<String> deliveredMeaningUnits,
            List<String> omittedMeaningUnits,
            List<String> misunderstoodMeaningUnits,
            List<String> addedInformation,
            List<ProfileSignal> profileSignals,
            boolean profileEligible,
            List<AssistanceUsage> assistanceUsage,
            Map<String, Object> debugMetadata
    ) {
    }

    public record Overall(
            Double score,
            int evaluatedTaskCount,
            int totalTaskCount
    ) {
    }

    public record EvaluationResponse(
            String requestId,
            Long itemId,
            Long attemptId,
            String evaluationVersion,
            String scoringPolicyVersion,
            String profilePolicyVersion,
            List<TaskResult> tasks,
            Overall overall,
            Map<String, Object> usage
    ) {
    }

    public record RecommendationEvidenceSummary(
            List<String> sources,
            int count,
            double recentAverage
    ) {
    }

    public record RecommendationExplanationRequest(
            String requestId,
            String idempotencyKey,
            String type,
            String targetMetric,
            String recommendedActivity,
            String recommendedTask,
            RecommendationEvidenceSummary evidenceSummary,
            String originLanguage,
            String policyVersion,
            String modelConfigVersion
    ) {
    }

    public record RecommendationExplanationResponse(
            String requestId,
            String targetMetric,
            String recommendedActivity,
            String recommendedTask,
            String explanation,
            String ctaLabel,
            String explanationVersion,
            Map<String, Object> usage
    ) {
    }
}
