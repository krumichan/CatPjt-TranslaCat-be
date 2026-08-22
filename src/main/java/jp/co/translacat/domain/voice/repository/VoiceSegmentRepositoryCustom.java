package jp.co.translacat.domain.voice.repository;

import jp.co.translacat.domain.voice.entity.VoiceSegment;
import jp.co.translacat.domain.voice.enums.VoiceChannel;

import java.util.List;
import java.util.Optional;

public interface VoiceSegmentRepositoryCustom {

    Optional<VoiceSegment> findOwnedForUpdate(
            Long segmentId,
            String sessionId,
            Long userId
    );

    long findMaxSequence(
            String sessionId,
            VoiceChannel channel
    );

    List<VoiceSegment> findPage(
            String sessionId,
            Long cursor,
            int limit
    );
}
