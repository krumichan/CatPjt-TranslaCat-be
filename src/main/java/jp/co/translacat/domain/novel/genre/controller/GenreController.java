package jp.co.translacat.domain.novel.genre.controller;

import io.swagger.v3.oas.annotations.Operation;
import jp.co.translacat.domain.common.enums.PlatformCode;
import jp.co.translacat.domain.novel.genre.dto.GenreResponseDto;
import jp.co.translacat.domain.novel.genre.service.GenreService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/{platformCode}")
@RequiredArgsConstructor
public class GenreController {
    private final GenreService genreService;

    @GetMapping("/genres")
    @Operation(summary = "장르 조회", description = "임의 플랫폼에 대한 모든 장르를 조회한다.")
    public ResponseDto<List<GenreResponseDto>> genres(@PathVariable PlatformCode platformCode) {
        return ResponseUtil.ok(genreService.list(platformCode));
    }
}
