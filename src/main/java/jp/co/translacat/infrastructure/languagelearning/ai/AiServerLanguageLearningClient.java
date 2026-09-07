package jp.co.translacat.infrastructure.languagelearning.ai;

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
import jp.co.translacat.domain.languagelearning.ai.port.LanguageLearningAiClient;
import jp.co.translacat.infrastructure.client.ai.server.AiServerClient;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiServerLanguageLearningClient
        implements LanguageLearningAiClient {

    private final AiServerClient aiServerClient;

    @Override
    public AiPracticeGenerationResponseDto generatePractice(
            AiPracticeGenerationRequestDto request
    ) {
        return aiServerClient.callLanguageLearningPracticeGeneration(request);
    }

    @Override
    public AiDailyWritingGenerationResponseDto generateDaily(
            AiDailyWritingGenerationRequestDto request
    ) {
        return aiServerClient.callLanguageLearningDailyGeneration(request);
    }

    @Override
    public AiWritingEvaluationResponseDto evaluate(
            AiWritingEvaluationRequestDto request
    ) {
        return aiServerClient.callLanguageLearningEvaluation(request);
    }

    @Override
    public AiLevelTestQuestionResponseDto generateLevelTestQuestion(
            AiLevelTestQuestionRequestDto request
    ) {
        return aiServerClient.callLanguageLearningLevelTestQuestion(request);
    }

    @Override
    public AiLevelTestQuestionResponseDto generateLevelTestPoolQuestion(
            AiLevelTestQuestionRequestDto request
    ) {
        return aiServerClient.callLanguageLearningLevelTestPoolQuestion(request);
    }

    @Override
    public AiLevelTestEvaluationResponseDto evaluateLevelTestText(
            AiLevelTestTextEvaluationRequestDto request
    ) {
        return aiServerClient.callLanguageLearningLevelTestTextEvaluation(
                request
        );
    }

    @Override
    public AiLevelTestEvaluationResponseDto evaluateLevelTestSpeaking(
            AiLevelTestSpeakingEvaluationRequestDto request,
            byte[] audio,
            String fileName,
            String contentType
    ) {
        return aiServerClient.callLanguageLearningLevelTestSpeakingEvaluation(
                request,
                audio,
                fileName,
                contentType
        );
    }
}
