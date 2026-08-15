package jp.co.translacat.domain.languagelearning.speaking.topic.controller;

import jp.co.translacat.domain.languagelearning.speaking.topic.dto.request.SpeakingTopicUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.topic.dto.response.SpeakingTopicResponseDto;
import jp.co.translacat.domain.languagelearning.speaking.topic.service.SpeakingTopicCommandService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/language-learning/speaking/topics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSpeakingTopicController {

    private final SpeakingTopicCommandService commandService;

    @PatchMapping("/{topicId}")
    public ResponseDto<SpeakingTopicResponseDto> update(
            @PathVariable Long topicId,
            @RequestBody SpeakingTopicUpdateRequestDto request
    ) {
        return ResponseUtil.ok(commandService.update(topicId, request));
    }
}
