package jp.co.translacat.domain.novel.novel.controller;

import io.swagger.v3.oas.annotations.Operation;
import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.novel.novel.dto.NovelPageResponseDto;
import jp.co.translacat.domain.novel.novel.service.NovelService;
import jp.co.translacat.domain.novel.novel.service.NovelMetadataQueryService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/{platformCode}/novels")
@RequiredArgsConstructor
public class NovelController {
    private final NovelService novelService;
    private final NovelMetadataQueryService metadata;

    @GetMapping("/{novelId}")
    @Operation(summary = "에피소드 목록", description = "임의 소설에 대한 에피소드 목록을 조회한다.")
    public ResponseDto<NovelPageResponseDto> episodes(
            @PathVariable PlatformCode platformCode,
            @PathVariable String novelId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(required = false) String targetLanguage) {
        // 기존 한국어 조회는 그대로 사용하고 영어는 원문·완료 캐시만 표시한다.
        String target = jp.co.translacat.novel.domain.NovelTargetLanguage.normalize(targetLanguage);
        if ("ko".equals(target)) return ResponseUtil.ok(novelService.episodes(platformCode, novelId, page));
        var source = novelService.episodes(platformCode, novelId, page, target);

        // 인증된 동일 사용자의 영어 목록만 재사용하고 그 방문 언어를 최근 기록에 남긴다.
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        Long actor = authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof jp.co.translacat.global.security.UserPrincipal principal
                ? principal.getId() : null;
        return ResponseUtil.ok(metadata.english(source, platformCode.name().toLowerCase(java.util.Locale.ROOT), novelId, actor));
    }
}
