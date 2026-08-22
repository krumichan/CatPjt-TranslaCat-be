package jp.co.translacat.domain.voice.enums;

import java.util.Set;

public enum VoiceMode {
    MIC,
    MEDIA,
    MEETING;

    public Set<VoiceChannel> allowedChannels() {
        return switch (this) {
            case MIC -> Set.of(VoiceChannel.SELF);
            case MEDIA -> Set.of(VoiceChannel.REMOTE);
            case MEETING -> Set.of(
                    VoiceChannel.SELF,
                    VoiceChannel.REMOTE
            );
        };
    }
}
