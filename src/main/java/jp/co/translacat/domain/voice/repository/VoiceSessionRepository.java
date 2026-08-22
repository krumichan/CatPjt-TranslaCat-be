package jp.co.translacat.domain.voice.repository;

import jp.co.translacat.domain.voice.entity.VoiceSession;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VoiceSessionRepository
        extends JpaRepository<VoiceSession, String>, VoiceSessionRepositoryCustom {

    Optional<VoiceSession> findByIdAndUser_Id(
            String id,
            Long userId
    );
}
