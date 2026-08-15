package jp.co.translacat.domain.languagelearning.speaking.topic.service;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingTopicCategory;
import jp.co.translacat.domain.languagelearning.speaking.topic.dto.response.SpeakingTopicResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.topic.entity.SpeakingTopic;
import jp.co.translacat.domain.languagelearning.speaking.topic.repository.SpeakingTopicRepository;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpeakingTopicQueryService {

    private final SpeakingTopicRepository repository;

    public List<SpeakingTopicResponseDto> getTopics(
            String learningLanguage,
            SpeakingTopicCategory category
    ) {
        List<SpeakingTopic> topics;

        if (learningLanguage != null && !learningLanguage.isBlank()) {
            topics = repository
                    .findAllByActiveTrueAndLearningLanguageOrderBySortOrderAscIdAsc(
                            learningLanguage.trim()
                    );
        } else if (category != null) {
            topics = repository
                    .findAllByActiveTrueAndCategoryOrderBySortOrderAscIdAsc(
                            category
                    );
        } else {
            topics = repository.findAllByActiveTrueOrderBySortOrderAscIdAsc();
        }

        return topics.stream()
                .filter(topic -> category == null
                        || topic.getCategory() == category)
                .map(this::toResponse)
                .toList();
    }

    public SpeakingTopic getActiveEntity(Long topicId) {
        return repository.findByIdAndActiveTrue(topicId)
                .orElseThrow(() -> new BusinessException(
                        "Speaking Topic을 찾을 수 없습니다.",
                        LanguageLearningErrorCode.SPEAKING_TOPIC_NOT_FOUND
                ));
    }

    public SpeakingTopicResponseDto toResponse(SpeakingTopic topic) {
        return new SpeakingTopicResponseDto(
                topic.getId(),
                topic.getTopicCode(),
                topic.getCategory(),
                topic.getTitle(),
                topic.getDescription(),
                topic.getOriginLanguage(),
                topic.getLearningLanguage(),
                topic.getRecommendedLevel(),
                topic.getRecommendedStartMode(),
                topic.getSortOrder(),
                topic.getVersion()
        );
    }
}
