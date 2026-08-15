package jp.co.translacat.domain.languagelearning.speaking.ai.port;

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

    byte[] getAudio(String audioReference);
}
