package jp.co.translacat.domain.user.repository;

import jp.co.translacat.domain.user.entity.RecentView;
import jp.co.translacat.domain.user.enums.RecentViewType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecentViewRepository extends JpaRepository<RecentView, Long> {
    Optional<RecentView> findByUserIdAndPlatformIdAndRecentViewTypeAndNovelIdAndEpisodeIdIsNull(
        Long userId, Long platformId, RecentViewType recentViewType, String novelId
    );

    Optional<RecentView> findByUserIdAndPlatformIdAndRecentViewTypeAndNovelIdAndEpisodeId(
        Long userId, Long platformId, RecentViewType recentViewType, String novelId, String episodeId
    );

    @EntityGraph(attributePaths = {"platform"})
    List<RecentView> findTop5ByUserIdAndRecentViewTypeOrderByViewedAtDesc(
        Long userId, RecentViewType recentViewType
    );
}
