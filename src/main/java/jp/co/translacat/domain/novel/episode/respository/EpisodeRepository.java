package jp.co.translacat.domain.novel.episode.respository;

import jp.co.translacat.domain.novel.episode.entity.Episode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EpisodeRepository extends JpaRepository<Episode, Long> {
    Optional<Episode> findByNovelIdAndIdentifier(Long novelId, String identifier);
    List<Episode> findAllByNovelIdAndIdentifierInOrderByIdentifierAsc(Long novelId, List<String> identifiers);
}
