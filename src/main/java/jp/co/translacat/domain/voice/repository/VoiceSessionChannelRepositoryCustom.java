package jp.co.translacat.domain.voice.repository;

import jp.co.translacat.domain.voice.entity.VoiceSessionChannel;
import jp.co.translacat.domain.voice.enums.VoiceChannel;

import java.util.Optional;

public interface VoiceSessionChannelRepositoryCustom {

    Optional<VoiceSessionChannel> findOwnedForUpdate(
            String sessionId,
            VoiceChannel channel,
            Long userId
    );
}
