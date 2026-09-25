package jp.co.translacat.domain.languagelearning.ai.port;

import jp.co.translacat.domain.languagelearning.ai.dto.request.AiDailyWritingGenerationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiPracticeGenerationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiWritingEvaluationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiDailyWritingGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiPracticeGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiWritingEvaluationResponseDto;

public interface LanguageLearningAiClient {

    AiPracticeGenerationResponseDto generatePractice(AiPracticeGenerationRequestDto request);

    AiDailyWritingGenerationResponseDto generateDaily(AiDailyWritingGenerationRequestDto request);

    AiWritingEvaluationResponseDto evaluate(AiWritingEvaluationRequestDto request);

}
