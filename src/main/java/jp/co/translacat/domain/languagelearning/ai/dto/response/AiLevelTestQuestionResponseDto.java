package jp.co.translacat.domain.languagelearning.ai.dto.response;

import jp.co.translacat.domain.languagelearning.ai.dto.model.LevelTestAiUsageDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.LevelTestInternalAnswerKeyDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.LevelTestOptionDto;
import jp.co.translacat.domain.languagelearning.ai.dto.model.LevelTestReferenceAudioDto;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestAnswerMode;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemType;
import jp.co.translacat.domain.languagelearning.quality.dto.DiversityMetadata;
import jp.co.translacat.domain.languagelearning.quality.dto.DiversitySummary;

import java.util.List;
import java.util.Map;

public record AiLevelTestQuestionResponseDto(
        String requestId,
        Long sessionId,
        int questionNumber,
        int totalQuestions,
        LevelTestDomain domain,
        LevelTestItemType itemType,
        int complexityBand,
        String instruction,
        String instructionLanguage,
        LevelTestAnswerMode answerMode,
        String answerLanguage,
        String promptText,
        List<LevelTestOptionDto> options,
        LevelTestInternalAnswerKeyDto internalAnswerKey,
        Map<String, Object> referencePayload,
        DiversityMetadata diversityMetadata,
        Integer maxAnswerLength,
        Integer maxAudioSeconds,
        String generationVersion,
        String promptVersion,
        DiversitySummary diversitySummary,
        LevelTestAiUsageDto usage,
        LevelTestReferenceAudioDto referenceAudio
) {
}
