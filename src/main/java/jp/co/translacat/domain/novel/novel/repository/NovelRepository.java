package jp.co.translacat.domain.novel.novel.repository;

import jp.co.translacat.domain.novel.novel.entity.Novel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NovelRepository extends JpaRepository<Novel, Long> {
    Optional<Novel> findByPlatformIdAndIdentifier(Long platformId, String identifier);
    List<Novel> findAllByPlatformIdAndIdentifierIn(Long platformId, List<String> identifiers);
}
