package jp.co.translacat.infrastructure.languagelearning.ai;

import jp.co.translacat.domain.languagelearning.speaking.ai.dto.request.AiSpeakingAssistanceRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.request.AiSpeakingEvaluationRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.request.AiSpeakingSessionStartRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.request.AiSpeakingTtsRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.request.AiSpeakingTurnProcessRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingAssistanceResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingConversationResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingEvaluationResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingSessionStartResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingTtsResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.ai.dto.response.AiSpeakingTurnProcessResponseDto;
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
    public byte[] getAudio(String audioReference) {
        return aiServerClient.callSpeakingAudio(audioReference);
    }
}
