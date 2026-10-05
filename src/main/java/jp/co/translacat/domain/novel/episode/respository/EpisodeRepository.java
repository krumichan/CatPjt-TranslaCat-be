package jp.co.translacat.domain.novel.episode.respository;

import jp.co.translacat.domain.novel.episode.entity.Episode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EpisodeRepository extends JpaRepository<Episode, Long> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Episode e where e.id = :id")
    Optional<Episode> findForContentUpdate(@Param("id") Long id);

    Optional<Episode> findByNovelIdAndIdentifier(Long novelId, String identifier);

    List<Episode> findAllByNovelIdAndIdentifierInOrderByIdentifierAsc(Long novelId, List<String> identifiers);
}
