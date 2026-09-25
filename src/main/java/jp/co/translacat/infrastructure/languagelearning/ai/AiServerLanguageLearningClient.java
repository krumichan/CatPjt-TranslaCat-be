package jp.co.translacat.infrastructure.languagelearning.ai;

import jp.co.translacat.domain.languagelearning.ai.dto.request.AiDailyWritingGenerationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiPracticeGenerationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.request.AiWritingEvaluationRequestDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiDailyWritingGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiPracticeGenerationResponseDto;
import jp.co.translacat.domain.languagelearning.ai.dto.response.AiWritingEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.ai.port.LanguageLearningAiClient;
import jp.co.translacat.infrastructure.client.ai.server.AiServerClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiServerLanguageLearningClient implements LanguageLearningAiClient {

    private final AiServerClient aiServerClient;

    @Override
    public AiPracticeGenerationResponseDto generatePractice(AiPracticeGenerationRequestDto request) {
        return aiServerClient.callLanguageLearningPracticeGeneration(request);
    }

    @Override
    public AiDailyWritingGenerationResponseDto generateDaily(AiDailyWritingGenerationRequestDto request) {
        return aiServerClient.callLanguageLearningDailyGeneration(request);
    }

    @Override
    public AiWritingEvaluationResponseDto evaluate(AiWritingEvaluationRequestDto request) {
        return aiServerClient.callLanguageLearningEvaluation(request);
    }

}
