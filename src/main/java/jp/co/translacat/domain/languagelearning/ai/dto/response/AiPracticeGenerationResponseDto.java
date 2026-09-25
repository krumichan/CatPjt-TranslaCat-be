package jp.co.translacat.domain.languagelearning.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import jp.co.translacat.domain.languagelearning.ai.dto.model.PersonalizedVocabularyPlanDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.PracticeGeneratedQuestionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.ReadingPassageBundleDto;
import jp.co.translacat.domain.languagelearning.common.enums.PracticeDomain;

import java.util.List;

public record AiPracticeGenerationResponseDto(
        String requestId,
        String promptVersion,
        PracticeDomain domain,
        String mode,
        int complexityBand,
        List<PracticeGeneratedQuestionDto> questions,
        PersonalizedVocabularyPlanDto vocabularyPlan,
        @JsonInclude(JsonInclude.Include.NON_NULL) ReadingPassageBundleDto readingBundle
) {
    public AiPracticeGenerationResponseDto(
            String requestId, String promptVersion, PracticeDomain domain,
            String mode, int complexityBand,
            List<PracticeGeneratedQuestionDto> questions,
            PersonalizedVocabularyPlanDto vocabularyPlan
    ) {
        this(requestId, promptVersion, domain, mode, complexityBand,
                questions, vocabularyPlan, null);
    }

    public AiPracticeGenerationResponseDto(
            String requestId,
            String promptVersion,
            PracticeDomain domain,
            String mode,
            int complexityBand,
            List<PracticeGeneratedQuestionDto> questions
    ) {
        this(requestId, promptVersion, domain, mode, complexityBand, questions, null);
    }
}
