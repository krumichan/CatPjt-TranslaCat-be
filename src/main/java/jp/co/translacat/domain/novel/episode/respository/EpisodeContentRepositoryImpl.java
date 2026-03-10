package jp.co.translacat.domain.novel.episode.respository;

import jp.co.translacat.domain.novel.episode.entity.EpisodeContent;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSourceUtils;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class EpisodeContentRepositoryImpl implements EpisodeContentBatchRepository {
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    public void batchInsertAll(List<EpisodeContent> contents) {
        if (contents.isEmpty()) return;

        String sql = """
            INSERT INTO episode_content (
                episode_id, sequence, content, content_ja, content_ko, 
                created_at, updated_at, created_by, updated_by
            ) VALUES (
                :episode.id, :sequence, :content, :contentJa, :contentKo, 
                NOW(), NOW(), :createdBy, :updatedBy
            )
            """;

        SqlParameterSource[] batch = SqlParameterSourceUtils.createBatch(contents);

        namedParameterJdbcTemplate.batchUpdate(sql, batch);
    }

    @Override
    public void batchUpdateAll(List<EpisodeContent> contents) {
        if (contents.isEmpty()) return;

        String sql = """
        UPDATE episode_content 
        SET 
            content_ja = :contentJa,
            updated_at = NOW(),
            updated_by = :updatedBy
        WHERE id = :id
        """;

        SqlParameterSource[] batch = SqlParameterSourceUtils.createBatch(contents);

        namedParameterJdbcTemplate.batchUpdate(sql, batch);
    }
}
