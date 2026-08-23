package jp.co.translacat.domain.languagelearning.listening.ai.port;

import jp.co.translacat.domain.languagelearning.listening.ai.dto.AiListeningContract;

public interface ListeningAiClient {

    AiListeningContract.GenerationResponse generateSet(
            AiListeningContract.GenerationRequest request
    );

    AiListeningContract.TtsResponse synthesize(
            AiListeningContract.TtsRequest request
    );

    byte[] getAudio(String audioReference);

    AiListeningContract.EvaluationResponse evaluateDictation(
            AiListeningContract.DictationRequest request
    );

    AiListeningContract.EvaluationResponse evaluateInterpretation(
            AiListeningContract.InterpretationRequest request
    );

    AiListeningContract.EvaluationResponse evaluateRepeat(
            AiListeningContract.RepeatRequest request,
            byte[] audioBytes,
            String fileName,
            String contentType
    );

    AiListeningContract.RecommendationExplanationResponse
    explainRecommendation(
            AiListeningContract.RecommendationExplanationRequest request
    );
}
