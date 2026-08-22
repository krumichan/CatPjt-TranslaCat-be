package jp.co.translacat.domain.voice.dto.request;

import jp.co.translacat.domain.voice.enums.VoiceChannel;
import jp.co.translacat.domain.voice.enums.VoiceMode;
import jp.co.translacat.domain.voice.enums.VoiceSourceLanguageMode;

import java.util.Map;

public record VoiceSessionCreateRequestDto(
        VoiceMode mode,
        VoiceSourceLanguageMode sourceLanguageMode,
        String targetLanguage,
        Boolean saveTranscript,
        Map<VoiceChannel, String> manualSourceLanguages
) {
}
