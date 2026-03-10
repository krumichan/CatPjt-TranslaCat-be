package jp.co.translacat.domain.novel.ranking.novel.controller;

import io.swagger.v3.oas.annotations.Operation;
import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.novel.ranking.novel.dto.NovelRankingPageResponseDto;
import jp.co.translacat.domain.novel.ranking.novel.dto.NovelRankingPeriodResponseDto;
import jp.co.translacat.domain.novel.ranking.novel.service.NovelRankingService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/{platformCode}/ranking")
@RequiredArgsConstructor
public class NovelRankingController {
    private final NovelRankingService novelRankingService;

    @GetMapping("/periods")
    @Operation(summary = "랭킹 조회 주기 목록", description = "주기별 소설 랭킹을 조회하기 위한 주기 목록을 조회한다.")
    public ResponseDto<List<NovelRankingPeriodResponseDto>> periods(
            @PathVariable PlatformCode platformCode) {
        return ResponseUtil.ok(novelRankingService.periods(platformCode));
    }

    @GetMapping("/novels/{period}/{genreId}")
    @Operation(summary = "소설 랭킹", description = "임의 플랫폼 및 장르에 대하여 랭킹 순위로 소설을 표시한다.")
    public ResponseDto<NovelRankingPageResponseDto> rankings(
            @PathVariable PlatformCode platformCode,
            @PathVariable String period,
            @PathVariable String genreId,
            @RequestParam(value = "page", defaultValue = "1") int page) {
        return ResponseUtil.ok(this.novelRankingService.list(
                platformCode, period, genreId, page));
    }
}
