package jp.co.translacat.domain.novel.platform.controller;

import io.swagger.v3.oas.annotations.Operation;
import jp.co.translacat.domain.novel.platform.dto.PlatformResponseDto;
import jp.co.translacat.domain.novel.platform.service.PlatformService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/platforms")
@RequiredArgsConstructor
public class PlatformController {
    private final PlatformService platformService;

    @GetMapping
    @Operation(summary = "플랫폼 조회", description = "모든 플랫폼을 조회한다.")
    public ResponseDto<List<PlatformResponseDto>> platforms() {
        return ResponseUtil.ok(platformService.platforms());
    }
}
