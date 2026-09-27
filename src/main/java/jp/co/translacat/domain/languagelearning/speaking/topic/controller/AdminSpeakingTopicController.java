package jp.co.translacat.domain.languagelearning.speaking.topic.controller;

import jp.co.translacat.domain.languagelearning.speaking.port.SpeakingGateway;
import jp.co.translacat.domain.languagelearning.speaking.topic.dto.request.SpeakingTopicUpdateRequestDto;
import jp.co.translacat.domain.languagelearning.speaking.topic.dto.response.SpeakingTopicResponseDto;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/language-learning/speaking/topics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSpeakingTopicController {

    private final SpeakingGateway gateway;

    @PatchMapping("/{topicId}")
    public ResponseDto<SpeakingTopicResponseDto> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long topicId,
            @RequestBody SpeakingTopicUpdateRequestDto request
    ) {
        return ResponseUtil.ok(gateway.adminPatch(SecurityUtil.getLoginUserId(principal),
                "/admin/topics/" + topicId, request, SpeakingTopicResponseDto.class));
    }
}
