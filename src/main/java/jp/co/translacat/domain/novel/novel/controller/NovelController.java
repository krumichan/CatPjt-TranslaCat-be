package jp.co.translacat.domain.novel.novel.controller;

import io.swagger.v3.oas.annotations.Operation;
import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.novel.novel.dto.NovelPageResponseDto;
import jp.co.translacat.domain.novel.novel.service.NovelService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/{platformCode}/novels")
@RequiredArgsConstructor
public class NovelController {
    private final NovelService novelService;

    @GetMapping("/{novelId}")
    @Operation(summary = "에피소드 목록", description = "임의 소설에 대한 에피소드 목록을 조회한다.")
    public ResponseDto<NovelPageResponseDto> episodes(
            @PathVariable PlatformCode platformCode,
            @PathVariable String novelId,
            @RequestParam(value = "page", defaultValue = "1") int page) {
        return ResponseUtil.ok(novelService.episodes(platformCode, novelId, page));
    }
}
