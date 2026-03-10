package jp.co.translacat.domain.novel.episode.controller;

import io.swagger.v3.oas.annotations.Operation;
import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.novel.episode.dto.EpisodeResponseDto;
import jp.co.translacat.domain.novel.novel.facade.NovelEpisodeFacade;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/{platformCode}/{novelIdentifier}/episodes")
@RequiredArgsConstructor
public class EpisodeController {
    private final NovelEpisodeFacade novelEpisodeFacade;

    @GetMapping("/{episodeId}")
    @Operation(summary = "에피소드 제목/내용", description = "임의 에피소드에 대한 제목 및 내용을 조회한다.")
    public ResponseDto<EpisodeResponseDto> episode(
            @PathVariable PlatformCode platformCode,
            @PathVariable String novelIdentifier,
            @PathVariable String episodeId) {
        return ResponseUtil.ok(this.novelEpisodeFacade.getTranslatedEpisode(
                platformCode, novelIdentifier, episodeId));
    }

    @GetMapping
    @Operation(summary = "단편 소설 제목/내용", description = "단편 소설의 제목 및 내용을 조회한다.")
    public ResponseDto<EpisodeResponseDto> episode(
            @PathVariable PlatformCode platformCode,
            @PathVariable String novelIdentifier) {
        return ResponseUtil.ok(this.novelEpisodeFacade.getTranslatedEpisode(
                platformCode, novelIdentifier));
    }
}
