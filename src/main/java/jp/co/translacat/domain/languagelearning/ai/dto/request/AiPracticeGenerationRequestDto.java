package jp.co.translacat.domain.languagelearning.ai.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import jp.co.translacat.domain.languagelearning.ai.dto.model.PersonalizedVocabularyPlanDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.PracticeGeneratedQuestionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.PracticeReviewTargetDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.ReadingPassageBundleDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.ReadingSlotTargetDto;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record AiPracticeGenerationRequestDto(
        String requestId,
        PracticeDomain domain,
        String mode,
        String originLanguage,
        String learningLanguage,
        int questionCount,
        int complexityBand,
        int easierCount,
        int currentCount,
        int challengeCount,
        List<String> selectedKeywords,
        List<String> weakSignals,
        List<String> recentMistakes,
        List<PracticeReviewTargetDto> reviewTargets,
        int reviewQuestionCount,
        LocalDate generationDate,
        List<PracticeGeneratedQuestionDto> previousQuestions,
        PersonalizedVocabularyPlanDto vocabularyPlan,
        boolean vocabularyPlanOnly,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, ReadingPassageBundleDto> readingBundles,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<ReadingSlotTargetDto> readingSlotTargets
) {
    public AiPracticeGenerationRequestDto(
            String requestId, PracticeDomain domain, String mode,
            String originLanguage, String learningLanguage, int questionCount,
            int complexityBand, int easierCount, int currentCount, int challengeCount,
            List<String> selectedKeywords, List<String> weakSignals, List<String> recentMistakes,
            List<PracticeReviewTargetDto> reviewTargets, int reviewQuestionCount,
            LocalDate generationDate, List<PracticeGeneratedQuestionDto> previousQuestions,
            PersonalizedVocabularyPlanDto vocabularyPlan, boolean vocabularyPlanOnly,
            Map<String, ReadingPassageBundleDto> readingBundles
    ) {
        this(requestId, domain, mode, originLanguage, learningLanguage, questionCount,
                complexityBand, easierCount, currentCount, challengeCount, selectedKeywords,
                weakSignals, recentMistakes, reviewTargets, reviewQuestionCount, generationDate,
                previousQuestions, vocabularyPlan, vocabularyPlanOnly, readingBundles, null);
    }
    public AiPracticeGenerationRequestDto(
            String requestId, PracticeDomain domain, String mode,
            String originLanguage, String learningLanguage, int questionCount,
            int complexityBand, int easierCount, int currentCount, int challengeCount,
            List<String> selectedKeywords, List<String> weakSignals, List<String> recentMistakes,
            List<PracticeReviewTargetDto> reviewTargets, int reviewQuestionCount,
            LocalDate generationDate, List<PracticeGeneratedQuestionDto> previousQuestions,
            PersonalizedVocabularyPlanDto vocabularyPlan, boolean vocabularyPlanOnly
    ) {
        this(requestId, domain, mode, originLanguage, learningLanguage, questionCount,
                complexityBand, easierCount, currentCount, challengeCount, selectedKeywords,
                weakSignals, recentMistakes, reviewTargets, reviewQuestionCount, generationDate,
                previousQuestions, vocabularyPlan, vocabularyPlanOnly, null, null);
    }
    public AiPracticeGenerationRequestDto(
            String requestId,
            PracticeDomain domain,
            String mode,
            String originLanguage,
            String learningLanguage,
            int questionCount,
            int complexityBand,
            int easierCount,
            int currentCount,
            int challengeCount,
            List<String> selectedKeywords,
            List<String> weakSignals,
            List<String> recentMistakes,
            List<PracticeReviewTargetDto> reviewTargets,
            int reviewQuestionCount,
            LocalDate generationDate,
            List<PracticeGeneratedQuestionDto> previousQuestions
    ) {
        this(
                requestId, domain, mode, originLanguage, learningLanguage, questionCount,
                complexityBand, easierCount, currentCount, challengeCount, selectedKeywords,
                weakSignals, recentMistakes, reviewTargets, reviewQuestionCount, generationDate,
                previousQuestions, null, false
        );
    }

    public AiPracticeGenerationRequestDto(
            String requestId,
            PracticeDomain domain,
            String mode,
            String originLanguage,
            String learningLanguage,
            int questionCount,
            int complexityBand,
            int easierCount,
            int currentCount,
            int challengeCount,
            List<String> selectedKeywords,
            List<String> weakSignals,
            List<String> recentMistakes,
            List<PracticeReviewTargetDto> reviewTargets,
            int reviewQuestionCount,
            LocalDate generationDate,
            List<PracticeGeneratedQuestionDto> previousQuestions,
            PersonalizedVocabularyPlanDto vocabularyPlan
    ) {
        this(
                requestId, domain, mode, originLanguage, learningLanguage, questionCount,
                complexityBand, easierCount, currentCount, challengeCount, selectedKeywords,
                weakSignals, recentMistakes, reviewTargets, reviewQuestionCount, generationDate,
                previousQuestions, vocabularyPlan, false
        );
    }
}
