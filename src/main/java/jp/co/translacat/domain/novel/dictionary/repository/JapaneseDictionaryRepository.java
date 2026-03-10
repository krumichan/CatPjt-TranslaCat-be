package jp.co.translacat.domain.novel.dictionary.repository;

import jp.co.translacat.domain.novel.dictionary.entity.JapaneseDictionary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JapaneseDictionaryRepository extends JpaRepository<JapaneseDictionary, Long> {
}
