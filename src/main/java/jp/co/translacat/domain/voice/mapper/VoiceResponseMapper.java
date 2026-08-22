package jp.co.translacat.domain.voice.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.voice.dto.response.VoiceChannelResponseDto;
import jp.co.translacat.domain.voice.dto.response.VoiceSegmentResponseDto;
import jp.co.translacat.domain.voice.dto.response.VoiceSessionResponseDto;
import jp.co.translacat.domain.voice.entity.VoiceSegment;
import jp.co.translacat.domain.voice.entity.VoiceSession;
import jp.co.translacat.domain.voice.entity.VoiceSessionChannel;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class VoiceResponseMapper {

    private final ObjectMapper objectMapper;

    public VoiceSessionResponseDto toSessionResponse(
            VoiceSession session,
            List<VoiceSessionChannel> channels
    ) {
        return new VoiceSessionResponseDto(
                session.getId(),
                session.getMode(),
                session.getSourceLanguageMode(),
                session.getTargetLanguage(),
                session.isSaveTranscript(),
                session.getStatus(),
                session.getTitle(),
                session.getProcessedAudioMs(),
                session.getCreatedAt(),
                session.getStartedAt(),
                session.getCompletedAt(),
                channels.stream()
                        .map(this::toChannelResponse)
                        .toList()
        );
    }

    public VoiceChannelResponseDto toChannelResponse(
            VoiceSessionChannel channel
    ) {
        return new VoiceChannelResponseDto(
                channel.getChannel(),
                channel.getStatus(),
                channel.getManualSourceLanguage(),
                channel.getLastLockedLanguage(),
                channel.getReconnectCount()
        );
    }

    public VoiceSegmentResponseDto toSegmentResponse(
            VoiceSegment segment
    ) {
        return new VoiceSegmentResponseDto(
                segment.getId(),
                segment.getChannel(),
                segment.getUtteranceKey(),
                segment.getUtteranceSequence(),
                segment.getStartedAtOffsetMs(),
                segment.getEndedAtOffsetMs(),
                segment.getSpeechDurationMs(),
                segment.getStatus(),
                segment.getDetectedLanguage(),
                segment.getLanguageConfidence(),
                segment.getLockedLanguage(),
                segment.getSourceText(),
                readJson(segment.getSourceReadingTokens()),
                segment.getTargetLanguage(),
                segment.getTranslatedText(),
                segment.isTranslationSkipped(),
                segment.getErrorCode(),
                segment.getRetryCount(),
                segment.getEndpointingMs(),
                segment.getSttFinalizeMs(),
                segment.getTranslationMs(),
                segment.getAiTotalAfterSpeechMs(),
                segment.getBeRelayAndPersistMs(),
                segment.getTotalAfterSpeechMs()
        );
    }

    private JsonNode readJson(String value) {
        if (value == null || value.isBlank()) {
            return objectMapper.createArrayNode();
        }

        try {
            return objectMapper.readTree(value);
        } catch (Exception ignored) {
            return objectMapper.createArrayNode();
        }
    }
}
