package jp.co.translacat.domain.languagelearning.ai.port;

import jp.co.translacat.domain.languagelearning.ai.dto.request.AiDailyWritingGenerationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiLevelTestQuestionRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiLevelTestSpeakingEvaluationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiLevelTestTextEvaluationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiWritingEvaluationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiPracticeGenerationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiDailyWritingGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiLevelTestEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiLevelTestQuestionResponseDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiWritingEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiPracticeGenerationResponseDto;

public interface LanguageLearningAiClient {

    AiPracticeGenerationResponseDto generatePractice(
            AiPracticeGenerationRequestDto request
    );

    AiDailyWritingGenerationResponseDto generateDaily(
            AiDailyWritingGenerationRequestDto request
    );

    AiWritingEvaluationResponseDto evaluate(
            AiWritingEvaluationRequestDto request
    );

    AiLevelTestQuestionResponseDto generateLevelTestQuestion(
            AiLevelTestQuestionRequestDto request
    );

    default AiLevelTestQuestionResponseDto generateLevelTestPoolQuestion(
            AiLevelTestQuestionRequestDto request
    ) {
        return generateLevelTestQuestion(request);
    }

    AiLevelTestEvaluationResponseDto evaluateLevelTestText(
            AiLevelTestTextEvaluationRequestDto request
    );

    AiLevelTestEvaluationResponseDto evaluateLevelTestSpeaking(
            AiLevelTestSpeakingEvaluationRequestDto request,
            byte[] audio,
            String fileName,
            String contentType
    );
}
