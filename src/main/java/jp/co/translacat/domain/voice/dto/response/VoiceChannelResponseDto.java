package jp.co.translacat.domain.voice.dto.response;

import jp.co.translacat.domain.voice.enums.VoiceChannel;
import jp.co.translacat.domain.voice.enums.VoiceChannelStatus;

public record VoiceChannelResponseDto(
        VoiceChannel channel,
        VoiceChannelStatus status,
        String manualSourceLanguage,
        String lastLockedLanguage,
        int reconnectCount
) {
}
