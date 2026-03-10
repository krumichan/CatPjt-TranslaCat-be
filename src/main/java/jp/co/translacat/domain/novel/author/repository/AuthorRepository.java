package jp.co.translacat.domain.novel.author.repository;

import jp.co.translacat.domain.novel.author.entity.Author;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuthorRepository extends JpaRepository<Author, Long> {
    Optional<Author> findByPlatformIdAndIdentifier(Long platformId, String identifier);
    List<Author> findAllByPlatformIdAndIdentifierIn(Long platformId, List<String> identifiers);
}
