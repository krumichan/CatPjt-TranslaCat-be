package jp.co.translacat.domain.novel.genre.repository;

import jp.co.translacat.domain.novel.genre.entity.Genre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GenreRepository extends JpaRepository<Genre, Long> {
    List<Genre> findAllByPlatformId(Long platformId);
    Optional<Genre> findByIdentifier(String identifier);
    Optional<Genre> findByPlatformIdAndNameEndsWith(Long platformId, String name);
}
