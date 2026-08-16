package jp.co.translacat.domain.languagelearning.keyword.repository;

import jp.co.translacat.domain.languagelearning.common.enums.KeywordType;
import jp.co.translacat.domain.languagelearning.keyword.entity.SystemKeyword;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SystemKeywordRepository extends JpaRepository<SystemKeyword, Long> {
    List<SystemKeyword> findAllByActiveTrueOrderBySortOrderAscIdAsc();
    List<SystemKeyword> findAllByOrderBySortOrderAscIdAsc();
    boolean existsByNormalizedTextAndType(String normalizedText, KeywordType type);
    boolean existsByNormalizedTextAndTypeAndIdNot(String normalizedText, KeywordType type, Long id);
    boolean existsByParentKeywordId(Long parentKeywordId);
    boolean existsByParentKeywordIdAndActiveTrue(Long parentKeywordId);
}
