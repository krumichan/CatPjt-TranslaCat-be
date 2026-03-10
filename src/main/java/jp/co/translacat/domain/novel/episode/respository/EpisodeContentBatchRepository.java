package jp.co.translacat.domain.novel.episode.respository;

import jp.co.translacat.domain.novel.episode.entity.EpisodeContent;

import java.util.List;

public interface EpisodeContentBatchRepository {
    void batchInsertAll(List<EpisodeContent> contents);
    void batchUpdateAll(List<EpisodeContent> contents);
}
