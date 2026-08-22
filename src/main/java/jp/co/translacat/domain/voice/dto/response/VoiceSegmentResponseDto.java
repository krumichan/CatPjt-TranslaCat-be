package jp.co.translacat.domain.voice.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.translacat.domain.voice.enums.VoiceChannel;
import jp.co.translacat.domain.voice.enums.VoiceSegmentStatus;

public record VoiceSegmentResponseDto(
        Long id,
        VoiceChannel channel,
        String utteranceKey,
        long utteranceSequence,
        long startedAtOffsetMs,
        long endedAtOffsetMs,
        long speechDurationMs,
        VoiceSegmentStatus status,
        String detectedLanguage,
        Double languageConfidence,
        String lockedLanguage,
        String sourceText,
        JsonNode sourceReadingTokens,
        String targetLanguage,
        String translatedText,
        boolean translationSkipped,
        String errorCode,
        int retryCount,
        Long endpointingMs,
        Long sttFinalizeMs,
        Long translationMs,
        Long aiTotalAfterSpeechMs,
        Long beRelayAndPersistMs,
        Long totalAfterSpeechMs
) {
}
