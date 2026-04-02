package jp.co.translacat.domain.common.language.repository;

import jp.co.translacat.domain.common.language.entity.Language;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LanguageRepository extends JpaRepository<Language, String> {
}
