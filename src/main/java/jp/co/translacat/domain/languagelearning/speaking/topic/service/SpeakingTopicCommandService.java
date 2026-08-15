package jp.co.translacat.domain.languagelearning.speaking.topic.service;

import jp.co.translacat.domain.languagelearning.speaking.topic.dto.request.SpeakingTopicUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.topic.dto.response.SpeakingTopicResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.topic.entity.SpeakingTopic;
import jp.co.translacat.domain.languagelearning.speaking.topic.repository.SpeakingTopicRepository;
import jp.co.translacat.domain.languagelearning.support.LanguageLearningErrorCode;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SpeakingTopicCommandService {

    private final SpeakingTopicRepository repository;
    private final SpeakingTopicQueryService queryService;

    @Transactional
    public SpeakingTopicResponseDto update(
            Long topicId,
            SpeakingTopicUpdateRequestDto request
    ) {
        SpeakingTopic topic = repository.findById(topicId)
                .orElseThrow(() -> new BusinessException(
                        "Speaking Topic을 찾을 수 없습니다.",
                        LanguageLearningErrorCode.SPEAKING_TOPIC_NOT_FOUND
                ));

        topic.update(
                request.title(),
                request.description(),
                request.recommendedLevel(),
                request.recommendedStartMode(),
                request.sortOrder(),
                request.active()
        );

        return queryService.toResponse(topic);
    }
}
