package jp.co.translacat.domain.novel.search.novel.controller;

import io.swagger.v3.oas.annotations.Operation;
import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.novel.search.novel.dto.NovelSearchPageResponseDto;
import jp.co.translacat.domain.novel.search.novel.service.NovelSearchService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/{platformCode}/search")
@RequiredArgsConstructor
public class NovelSearchController {
    private final NovelSearchService novelSearchService;

    @GetMapping("/novels")
    @Operation(summary = "소설 검색", description = "임의 플랫폼의 검색 키워드에 따른 소설들을 표시한다.")
    public ResponseDto<NovelSearchPageResponseDto> novels(
            @PathVariable PlatformCode platformCode,
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page) {
        return ResponseUtil.ok(this.novelSearchService.list(platformCode, keyword, page));
    }
}
