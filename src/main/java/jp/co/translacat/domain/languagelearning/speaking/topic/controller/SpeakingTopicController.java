package jp.co.translacat.domain.languagelearning.speaking.topic.controller;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingTopicCategory;
import jp.co.translacat.domain.languagelearning.speaking.topic.dto.response.SpeakingTopicResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.topic.service.SpeakingTopicQueryService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/language-learning/speaking/topics")
@RequiredArgsConstructor
public class SpeakingTopicController {

    private final SpeakingTopicQueryService queryService;

    @GetMapping
    public ResponseDto<List<SpeakingTopicResponseDto>> getTopics(
            @RequestParam(required = false) String learningLanguage,
            @RequestParam(required = false) SpeakingTopicCategory category
    ) {
        return ResponseUtil.ok(
                queryService.getTopics(learningLanguage, category)
        );
    }
}
