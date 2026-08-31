package jp.co.translacat.domain.languagelearning.level.dto.response;

import jp.co.translacat.domain.languagelearning.common.enums.LevelTestAnswerMode;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestDomain;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemStatus;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestItemType;
import jp.co.translacat.domain.languagelearning.common.enums.LevelTestSessionType;

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
        Integer maxAnswerLength,
        Integer maxAudioSeconds,
        LevelTestItemStatus status,
        String evaluationReasonCode
) {
}
