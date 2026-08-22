package jp.co.translacat.domain.voice.model;

import jp.co.translacat.domain.voice.enums.VoiceChannel;
import jp.co.translacat.domain.voice.enums.VoiceMode;
import jp.co.translacat.domain.voice.enums.VoiceSourceLanguageMode;

public record VoiceStreamContext(
        Long userId,
        String sessionId,
        VoiceChannel channel,
        String connectionId,
        VoiceMode mode,
        VoiceSourceLanguageMode sourceLanguageMode,
        String manualSourceLanguage,
        String lastLockedLanguage,
        String targetLanguage,
        String policySnapshot,
        long sequenceOffset
) {
}
