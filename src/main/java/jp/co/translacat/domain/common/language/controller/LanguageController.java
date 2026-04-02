package jp.co.translacat.domain.common.language.controller;

import io.swagger.v3.oas.annotations.Operation;
import jp.co.translacat.domain.common.language.dto.RespLanguageDto;
import jp.co.translacat.domain.common.language.service.LanguageService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/languages")
@RequiredArgsConstructor
public class LanguageController {
    private final LanguageService languageService;

    @GetMapping
    @Operation(summary = "언어 목록 조회", description = "언어 목록을 조회한다.")
    public ResponseDto<List<RespLanguageDto>> languages() {
        return ResponseUtil.ok(this.languageService.languages());
    }
}
