package jp.co.translacat.domain.languagelearning.speaking.ai.port;

import jp.co.translacat.domain.languagelearning.speaking.ai.dto.request.*;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.*;

public interface SpeakingAiClient {

    AiSpeakingSessionStartResponseDto startSession(
            AiSpeakingSessionStartRequestDto request
    );

    AiSpeakingTurnProcessResponseDto processTurn(
            AiSpeakingTurnProcessRequestDto request,
            byte[] audioBytes,
            String fileName,
            String contentType
    );

    AiSpeakingConversationResponseDto generateResponse(
            AiSpeakingTurnProcessRequestDto request
    );

    AiSpeakingAssistanceResponseDto generateAssistance(
            AiSpeakingAssistanceRequestDto request
    );

    AiSpeakingTtsResponseDto synthesize(
            AiSpeakingTtsRequestDto request
    );

    AiSpeakingEvaluationResponseDto evaluate(
            AiSpeakingEvaluationRequestDto request
    );

    AiSpeakingCoachingResponseDto coach(AiSpeakingCoachingRequestDto request);

    byte[] getAudio(String audioReference);
}
