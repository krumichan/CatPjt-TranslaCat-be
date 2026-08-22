package jp.co.translacat.domain.voice.dto.response;

import jp.co.translacat.domain.voice.enums.VoiceMode;
import jp.co.translacat.domain.voice.enums.VoiceSessionStatus;
import jp.co.translacat.domain.voice.enums.VoiceSourceLanguageMode;

import java.time.LocalDateTime;
import java.util.List;

public record VoiceSessionResponseDto(
        String id,
        VoiceMode mode,
        VoiceSourceLanguageMode sourceLanguageMode,
        String targetLanguage,
        boolean saveTranscript,
        VoiceSessionStatus status,
        String title,
        long processedAudioMs,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        List<VoiceChannelResponseDto> channels
) {
}
