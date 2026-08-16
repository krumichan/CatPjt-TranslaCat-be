package jp.co.translacat.domain.languagelearning.keyword.repository;

import jp.co.translacat.domain.languagelearning.keyword.entity.SystemKeywordLocale;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SystemKeywordLocaleRepository
        extends JpaRepository<SystemKeywordLocale, Long> {

    List<SystemKeywordLocale> findAllBySystemKeywordIdInAndLocaleIn(
            Collection<Long> systemKeywordIds,
            Collection<String> locales
    );
}
