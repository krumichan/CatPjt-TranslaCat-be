package jp.co.translacat.domain.languagelearning.ai.dto.request;

import jp.co.translacat.domain.languagelearning.ai.dto.model.LevelTestPreviousResultDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.LevelTestReferenceAudioUploadDto;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemType;
import jp.co.translacat.domain.languagelearning.quality.dto.DiversityContext;

import java.util.List;

public record AiLevelTestQuestionRequestDto(
        String requestId,
        String idempotencyKey,
        Long sessionId,
        int questionNumber,
        int totalQuestions,
        LevelTestDomain domain,
        LevelTestItemType itemType,
        String originLanguage,
        String learningLanguage,
        int targetComplexityBand,
        List<LevelTestPreviousResultDto> previousResults,
        DiversityContext diversityContext,
        List<String> preferredScenarioCategories,
        String policyVersion,
        String modelConfigVersion,
        LevelTestReferenceAudioUploadDto referenceAudioUpload
) {
}
