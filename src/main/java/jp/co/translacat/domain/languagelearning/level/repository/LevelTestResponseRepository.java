package jp.co.translacat.domain.languagelearning.level.repository;

import jp.co.translacat.domain.languagelearning.level.entity.LevelTestResponse;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LevelTestResponseRepository
        extends JpaRepository<LevelTestResponse, Long> {

    Optional<LevelTestResponse> findByItemId(Long itemId);

    Optional<LevelTestResponse> findByItemIdAndIdempotencyKey(
            Long itemId,
            String idempotencyKey
    );

    List<LevelTestResponse> findAllByAudioObjectKeyIsNotNullAndAudioDeletedAtIsNullAndAudioRetentionUntilBefore(
            LocalDateTime now
    );
}
