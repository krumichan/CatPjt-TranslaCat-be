package jp.co.translacat.domain.voice.repository;

import jp.co.translacat.domain.voice.entity.VoiceSessionChannel;
import jp.co.translacat.domain.voice.enums.VoiceChannel;
import jp.co.translacat.domain.voice.enums.VoiceChannelStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VoiceSessionChannelRepository
        extends JpaRepository<VoiceSessionChannel, Long>, VoiceSessionChannelRepositoryCustom {

    List<VoiceSessionChannel> findBySession_IdOrderByChannelAsc(String sessionId);

    Optional<VoiceSessionChannel> findBySession_IdAndChannel(
            String sessionId,
            VoiceChannel channel
    );

    long countBySession_IdAndStatusIn(
            String sessionId,
            Collection<VoiceChannelStatus> statuses
    );

    void deleteBySession_Id(String sessionId);
}
