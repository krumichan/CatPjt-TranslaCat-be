package jp.co.translacat.domain.novel.episode.respository;

import jp.co.translacat.domain.novel.episode.entity.EpisodeContent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EpisodeContentRepository
        extends JpaRepository<EpisodeContent, Long>, EpisodeContentBatchRepository {
    List<EpisodeContent> findAllByEpisodeIdOrderBySequenceAsc(Long episodeId);
    void deleteAllByEpisodeId(Long episodeId);
    int countByEpisodeId(Long episodeId);

    List<EpisodeContent> findAllByContentContains(String content);
}
