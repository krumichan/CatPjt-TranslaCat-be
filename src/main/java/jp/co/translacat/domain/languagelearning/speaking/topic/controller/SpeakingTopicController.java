package jp.co.translacat.domain.languagelearning.speaking.topic.controller;

import jp.co.translacat.domain.languagelearning.speaking.common.enums.SpeakingTopicCategory;
import jp.co.translacat.domain.languagelearning.speaking.port.SpeakingGateway;
import jp.co.translacat.domain.languagelearning.speaking.topic.dto.response.SpeakingTopicResponseDto;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.ResponseUtil;
import jp.co.translacat.global.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/v1/language-learning/speaking/topics")
@RequiredArgsConstructor
public class SpeakingTopicController {

    private final SpeakingGateway gateway;

    @GetMapping
    public ResponseDto<List<SpeakingTopicResponseDto>> getTopics(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String learningLanguage,
            @RequestParam(required = false) SpeakingTopicCategory category
    ) {
        var path = UriComponentsBuilder.fromPath("/topics");
        if (learningLanguage != null) path.queryParam("learningLanguage", learningLanguage);
        if (category != null) path.queryParam("category", category.name());

        return ResponseUtil.ok(gateway.list(SecurityUtil.getLoginUserId(principal),
                path.build().encode().toUriString(), SpeakingTopicResponseDto.class));
    }
}
