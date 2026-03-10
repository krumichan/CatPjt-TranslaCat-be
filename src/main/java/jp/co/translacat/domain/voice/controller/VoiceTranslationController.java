package jp.co.translacat.domain.voice.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jp.co.translacat.domain.voice.dto.VoiceTranslationRequestDto;
import jp.co.translacat.domain.voice.dto.VoiceTranslationResponseDto;
import jp.co.translacat.domain.voice.service.VoiceTranslationService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/voice")
@RequiredArgsConstructor
public class VoiceTranslationController {

    private final VoiceTranslationService voiceTranslationService;

    @PostMapping("/translate")
    @Operation(summary = "실시간 일본어 번역 및 저장", description = "실시간으로 받은 일본어를 받아 번역한 후 저장한다.")
    private ResponseDto<VoiceTranslationResponseDto> translateAndSave(@RequestBody @Valid VoiceTranslationRequestDto requestDto) {
        return ResponseUtil.ok(this.voiceTranslationService.translateAndSave(requestDto));
    }
}
