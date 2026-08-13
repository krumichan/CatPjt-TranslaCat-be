package jp.co.translacat.domain.languagelearning.keyword.repository;

import jp.co.translacat.domain.languagelearning.keyword.entity.UserSystemKeywordSelection;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserSystemKeywordSelectionRepository extends JpaRepository<UserSystemKeywordSelection, Long> {
    @EntityGraph(attributePaths = "systemKeyword")
    List<UserSystemKeywordSelection> findAllByUserIdAndActiveTrue(Long userId);

    @EntityGraph(attributePaths = "systemKeyword")
    List<UserSystemKeywordSelection> findAllByUserId(Long userId);
    Optional<UserSystemKeywordSelection> findByUserIdAndSystemKeywordId(Long userId, Long systemKeywordId);
}
