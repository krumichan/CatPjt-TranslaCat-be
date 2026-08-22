package jp.co.translacat.domain.voice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.voice.dto.response.VoiceSegmentResponseDto;
import jp.co.translacat.domain.voice.entity.VoiceSegment;
import jp.co.translacat.domain.voice.enums.VoiceSegmentStatus;
import jp.co.translacat.domain.voice.mapper.VoiceResponseMapper;
import jp.co.translacat.domain.voice.model.VoiceTranslationRetryContext;
import jp.co.translacat.domain.voice.repository.VoiceSegmentRepository;
import jp.co.translacat.domain.voice.support.VoiceErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.infrastructure.client.ai.server.voice.dto.AiVoiceModelResponse;
import jp.co.translacat.infrastructure.client.ai.server.voice.dto.AiVoiceTranslationRetryResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VoiceTranslationRetryCommandService {

    private final VoiceSegmentRepository segmentRepository;
    private final VoiceResponseMapper responseMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public VoiceSegmentResponseDto apply(
            Long userId,
            String sessionId,
            Long segmentId,
            VoiceTranslationRetryContext context,
            AiVoiceTranslationRetryResponse response
    ) {
        VoiceSegment segment = segmentRepository
                .findOwnedForUpdate(
                        segmentId,
                        sessionId,
                        userId
                )
                .orElseThrow(this::notFound);

        if (segment.getStatus() != VoiceSegmentStatus.TRANSLATION_FAILED
                || !context.sourceText().equals(segment.getSourceText())) {
            throw new BusinessException(
                    "Voice segment changed while translation retry was running.",
                    VoiceErrorCode.RETRY_CONFLICT
            );
        }

        AiVoiceModelResponse model = response.model();
        segment.applyRetry(
                requireTranslatedText(response.translatedText()),
                writeJson(response.sourceReadingTokens()),
                response.translationSkipped(),
                model == null ? null : model.translationVersion(),
                model == null ? null : model.promptVersion()
        );

        return responseMapper.toSegmentResponse(segment);
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(
                    node == null || node.isMissingNode() || node.isNull()
                            ? objectMapper.createArrayNode()
                            : node
            );
        } catch (Exception e) {
            throw invalidAiResponse(
                    "Invalid Reading token response from AI."
            );
        }
    }

    private String requireTranslatedText(String translatedText) {
        if (translatedText == null || translatedText.isBlank()) {
            throw invalidAiResponse(
                    "AI translation retry response is invalid."
            );
        }
        return translatedText;
    }

    private BusinessException invalidAiResponse(String message) {
        return new BusinessException(
                message,
                VoiceErrorCode.INVALID_AI_RESPONSE
        );
    }

    private BusinessException notFound() {
        return new BusinessException(
                "Voice segment was not found.",
                VoiceErrorCode.NOT_FOUND
        );
    }
}
