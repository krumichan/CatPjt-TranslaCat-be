package jp.co.translacat.domain.languagelearning.ai.port;

import jp.co.translacat.domain.languagelearning.ai.dto.request.AiDailyWritingGenerationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiLevelTestQuestionRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiWritingEvaluationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiDailyWritingGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiLevelTestQuestionResponseDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiWritingEvaluationResponseDto;

public interface LanguageLearningAiClient {

    AiDailyWritingGenerationResponseDto generateDaily(
            AiDailyWritingGenerationRequestDto request
    );

    AiWritingEvaluationResponseDto evaluate(
            AiWritingEvaluationRequestDto request
    );

    AiLevelTestQuestionResponseDto generateLevelTestQuestion(
            AiLevelTestQuestionRequestDto request
    );
}
