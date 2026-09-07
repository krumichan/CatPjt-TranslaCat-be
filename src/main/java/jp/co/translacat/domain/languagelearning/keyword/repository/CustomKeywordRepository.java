package jp.co.translacat.domain.languagelearning.keyword.repository;

import jp.co.translacat.domain.languagelearning.keyword.entity.CustomKeyword;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomKeywordRepository extends JpaRepository<CustomKeyword, Long> {

    List<CustomKeyword> findAllByUserIdOrderByIdAsc(Long userId);

    List<CustomKeyword> findAllByUserIdAndActiveTrue(Long userId);

    Optional<CustomKeyword> findByIdAndUserId(Long id, Long userId);


    boolean existsByParentSystemKeywordId(Long parentSystemKeywordId);

    boolean existsByPendingParentSystemKeywordId(Long parentSystemKeywordId);
}
