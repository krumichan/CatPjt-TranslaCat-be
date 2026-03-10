package jp.co.translacat.domain.novel.dictionary.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jp.co.translacat.domain.novel.dictionary.dto.DictionaryRegisterDto;
import jp.co.translacat.domain.novel.dictionary.service.DictionaryService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dictionary")
@RequiredArgsConstructor
public class DictionaryController {
    private final DictionaryService dictionaryService;

    @PostMapping("/register")
    @Operation(summary = "일본어 사전 후리가나 추가", description = "일본어, 후리가나 조합으로 단어 추가 및 DB 갱신")
    public ResponseDto<Boolean> register(@RequestBody @Valid DictionaryRegisterDto dictionaryRegisterDto) {
        return ResponseUtil.ok(dictionaryService.register(dictionaryRegisterDto));
    }
}
