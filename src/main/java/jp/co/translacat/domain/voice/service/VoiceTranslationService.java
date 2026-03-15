package jp.co.translacat.domain.voice.service;

import jp.co.translacat.domain.novel.translation.model.TranslationUnit;
import jp.co.translacat.domain.voice.dto.SoundTranslationRequestDto;
import jp.co.translacat.domain.voice.dto.VoiceTranslationRequestDto;
import jp.co.translacat.domain.voice.dto.VoiceTranslationResponseDto;
import jp.co.translacat.domain.voice.model.VoiceTranslationEvent;
import jp.co.translacat.global.utils.SecurityUtil;
import jp.co.translacat.infrastructure.client.ai.ConversionExecutor;
import jp.co.translacat.infrastructure.client.ai.gemini.AiGeminiClient;
import jp.co.translacat.infrastructure.japanese.FuriganaProcessor;
import jp.co.translacat.infrastructure.scraping.syosetu.constant.AiGeminiConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceTranslationService {

    private final FuriganaProcessor furiganaProcessor;
    private final ApplicationEventPublisher eventPublisher;

    private final AiGeminiClient aiGeminiClient;
    private final ConversionExecutor conversionExecutor;

    @Transactional
    public VoiceTranslationResponseDto translateAndSave(VoiceTranslationRequestDto requestDto) {

        String rawJa = requestDto.getText();

        // 1. 병렬처리 생성 - Gemini 번역.
        CompletableFuture<String> translation = CompletableFuture.supplyAsync(
            () -> this.aiGeminiClient.call(AiGeminiConstant.VoiceRule, rawJa)
        );

        // 2. 병렬처리 생성 - 루비 변환.
        CompletableFuture<String> ruby = CompletableFuture.supplyAsync(
            () -> this.furiganaProcessor.convertToRuby(rawJa)
        );

        // 3. 비동기 처리 수행.
        String translated = translation.join();
        String rubyJa = ruby.join();

        // 4. 객체 생성.
        TranslationUnit unit = TranslationUnit.of(rawJa, rubyJa, translated);

        // 5. 번역 성공 시 비동기 저장 이벤트 발행
        this.eventPublisher.publishEvent(
            VoiceTranslationEvent.builder()
                .groupId(requestDto.getGroupId())
                .userEmail(SecurityUtil.getSafeUsername())
                .unit(unit)
                .build()
        );

        return VoiceTranslationResponseDto.of(unit);
    }

    @Transactional
    public VoiceTranslationResponseDto translateSoundAndSave(SoundTranslationRequestDto requestDto) {
        String sttResponse = this.conversionExecutor.stt(requestDto.getSound());

        // 1. 병렬처리 생성 - Gemini 번역.
        CompletableFuture<String> translation = CompletableFuture.supplyAsync(
            () -> this.aiGeminiClient.call(AiGeminiConstant.VoiceRule, sttResponse)
        );

        // 2. 병렬처리 생성 - 루비 변환.
        CompletableFuture<String> ruby = CompletableFuture.supplyAsync(
            () -> this.furiganaProcessor.convertToRuby(sttResponse)
        );

        // 3. 비동기 처리 수행.
        String translated = translation.join();
        String rubyJa = ruby.join();

        // 4. 객체 생성.
        TranslationUnit unit = TranslationUnit.of(sttResponse, rubyJa, translated);

        // 5. 번역 성공 시 비동기 저장 이벤트 발행
        this.eventPublisher.publishEvent(
            VoiceTranslationEvent.builder()
                .groupId(requestDto.getGroupId())
                .userEmail(SecurityUtil.getSafeUsername())
                .unit(unit)
                .build()
        );

        return VoiceTranslationResponseDto.of(unit);
    }
}
