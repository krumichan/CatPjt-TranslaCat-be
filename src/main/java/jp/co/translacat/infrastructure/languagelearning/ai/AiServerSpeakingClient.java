package jp.co.translacat.infrastructure.languagelearning.ai;

import jp.co.translacat.domain.languagelearning.speaking.ai.dto.request.*;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.*;
import jp.co.translacat.domain.languagelearning.speaking.ai.port.SpeakingAiClient;
import jp.co.translacat.infrastructure.client.ai.server.AiServerClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiServerSpeakingClient implements SpeakingAiClient {

    private final AiServerClient aiServerClient;

    @Override
    public AiSpeakingSessionStartResponseDto startSession(
            AiSpeakingSessionStartRequestDto request
    ) {
        return aiServerClient.callSpeakingSessionStart(request);
    }

    @Override
    public AiSpeakingTurnProcessResponseDto processTurn(
            AiSpeakingTurnProcessRequestDto request,
            byte[] audioBytes,
            String fileName,
            String contentType
    ) {
        return aiServerClient.callSpeakingTurnProcess(
                request,
                audioBytes,
                fileName,
                contentType
        );
    }

    @Override
    public AiSpeakingConversationResponseDto generateResponse(
            AiSpeakingTurnProcessRequestDto request
    ) {
        return aiServerClient.callSpeakingResponse(request);
    }

    @Override
    public AiSpeakingAssistanceResponseDto generateAssistance(
            AiSpeakingAssistanceRequestDto request
    ) {
        return aiServerClient.callSpeakingAssistance(request);
    }

    @Override
    public AiSpeakingTtsResponseDto synthesize(
            AiSpeakingTtsRequestDto request
    ) {
        return aiServerClient.callSpeakingTts(request);
    }

    @Override
    public AiSpeakingEvaluationResponseDto evaluate(
            AiSpeakingEvaluationRequestDto request
    ) {
        return aiServerClient.callSpeakingEvaluation(request);
    }

    @Override
    public AiSpeakingCoachingResponseDto coach(AiSpeakingCoachingRequestDto request) {
        return aiServerClient.callSpeakingCoaching(request);
    }

    @Override
    public byte[] getAudio(String audioReference) {
        return aiServerClient.callSpeakingAudio(audioReference);
    }
}
