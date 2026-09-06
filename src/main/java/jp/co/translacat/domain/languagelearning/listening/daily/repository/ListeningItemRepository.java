package jp.co.translacat.domain.languagelearning.listening.daily.repository;

import jp.co.translacat.domain.languagelearning.listening.common.enums.ListeningItemStatus;
import jp.co.translacat.domain.languagelearning.listening.daily.entity.ListeningItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ListeningItemRepository
        extends JpaRepository<ListeningItem, Long>,
        ListeningItemRepositoryCustom {

    List<ListeningItem>
    findAllByDailySetIdOrderByItemIndexAscReplacementSequenceAsc(Long dailySetId);

    List<ListeningItem>
    findAllByDailySetIdAndStatusInOrderByItemIndexAscReplacementSequenceDesc(
            Long dailySetId,
            Collection<ListeningItemStatus> statuses
    );

    Optional<ListeningItem>
    findFirstByDailySetIdAndItemIndexOrderByReplacementSequenceDesc(
            Long dailySetId,
            int itemIndex
    );

    Optional<ListeningItem> findByIdAndDailySetUserId(
            Long itemId,
            Long userId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ListeningItem> findLockedById(Long itemId);

    long countByDailySetId(Long dailySetId);

    boolean existsByDailySetUserIdAndDailySetLearningLanguageAndContentHash(
            Long userId,
            String learningLanguage,
            String contentHash
    );

    List<ListeningItem>
    findTop200ByDailySetUserIdAndDailySetLearningLanguageOrderByCreatedAtDesc(
            Long userId,
            String learningLanguage
    );

    List<ListeningItem>
    findTop50ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(
            ListeningItemStatus status,
            LocalDateTime updatedAt
    );

    List<ListeningItem>
    findTop100ByAudioObjectKeyIsNotNullAndAudioDeletedAtIsNullAndAudioRetentionUntilBeforeOrderByAudioRetentionUntilAsc(
            LocalDateTime now
    );
}
