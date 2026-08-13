package jp.co.translacat.domain.languagelearning.daily.repository;

import jp.co.translacat.domain.languagelearning.daily.entity.DailyWritingItem;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DailyWritingItemRepository extends JpaRepository<DailyWritingItem, Long> {

    List<DailyWritingItem> findAllByDailySetIdOrderByOrderNoAsc(Long dailySetId);

    Optional<DailyWritingItem> findByIdAndDailySetUserId(Long id, Long userId);
}
