package jp.co.translacat.domain.voice.repository;

import jp.co.translacat.domain.voice.entity.VoiceSegment;
import jp.co.translacat.domain.voice.enums.VoiceChannel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VoiceSegmentRepository
        extends JpaRepository<VoiceSegment, Long>, VoiceSegmentRepositoryCustom {

    Optional<VoiceSegment> findByIdAndSession_IdAndSession_User_Id(
            Long id,
            String sessionId,
            Long userId
    );

    Optional<VoiceSegment> findByTranscriptFinalEventId(String transcriptFinalEventId);

    Optional<VoiceSegment> findByPipelineCompletedEventId(String pipelineCompletedEventId);

    Optional<VoiceSegment> findBySession_IdAndChannelAndUtteranceSequence(
            String sessionId,
            VoiceChannel channel,
            long utteranceSequence
    );

    void deleteBySession_Id(String sessionId);
}
