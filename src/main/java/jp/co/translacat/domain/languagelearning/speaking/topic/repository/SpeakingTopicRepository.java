package jp.co.translacat.domain.languagelearning.speaking.topic.repository;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingTopicCategory;
import jp.co.translacat.domain.languagelearning.speaking.topic.entity.SpeakingTopic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpeakingTopicRepository
        extends JpaRepository<SpeakingTopic, Long> {

    List<SpeakingTopic> findAllByActiveTrueOrderBySortOrderAscIdAsc();

    List<SpeakingTopic> findAllByActiveTrueAndLearningLanguageOrderBySortOrderAscIdAsc(
            String learningLanguage
    );

    List<SpeakingTopic> findAllByActiveTrueAndCategoryOrderBySortOrderAscIdAsc(
            SpeakingTopicCategory category
    );

    Optional<SpeakingTopic> findByIdAndActiveTrue(Long id);

    boolean existsByTopicCodeAndVersion(String topicCode, int version);
}
