package jp.co.translacat.domain.voice.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jp.co.translacat.domain.voice.dto.SoundTranslationRequestDto;
import jp.co.translacat.domain.voice.dto.VoiceTranslationRequestDto;
import jp.co.translacat.domain.voice.dto.VoiceTranslationResponseDto;
import jp.co.translacat.domain.voice.service.VoiceTranslationService;
import jp.co.translacat.global.dto.ResponseDto;
import jp.co.translacat.global.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
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

    @PostMapping("/translate/sound")
    @Operation(summary = "실시간 시스템 사운드 일본어 번역 및 저장", description = "실시간으로 시스템으로부터 일본어 음성을 받아 번역한 후 저장한다.")
    private ResponseDto<VoiceTranslationResponseDto> translateSoundAndSave(@ModelAttribute SoundTranslationRequestDto requestDto) {
        return ResponseUtil.ok(this.voiceTranslationService.translateSoundAndSave(requestDto));
    }
}
