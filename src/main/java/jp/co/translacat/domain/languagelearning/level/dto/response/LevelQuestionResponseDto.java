package jp.co.translacat.domain.languagelearning.level.dto.response;

import jp.co.translacat.domain.languagelearning.common.enums.*;

import java.util.List;

public record LevelQuestionResponseDto(
        Long sessionId,
        LevelTestSessionType sessionType,
        Long itemId,
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
        List<LevelTestOptionResponseDto> options,
        String emphasisText,
        LevelTestTaskGuidanceResponseDto taskGuidance,
        boolean referenceAudioAvailable,
        String repeatReferenceText,
        Integer referencePlaybackLimit,
        Integer maxAnswerLength,
        Integer maxAudioSeconds,
        LevelTestItemStatus status,
        String evaluationReasonCode
) {
}
