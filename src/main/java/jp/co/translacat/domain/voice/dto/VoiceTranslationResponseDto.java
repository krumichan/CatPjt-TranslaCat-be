package jp.co.translacat.domain.voice.dto;

import jp.co.translacat.domain.novel.translation.model.TranslationUnit;
import lombok.Getter;

@Getter
public class VoiceTranslationResponseDto {

    private TranslationUnit text;

    public static VoiceTranslationResponseDto of(TranslationUnit text) {
        VoiceTranslationResponseDto response = new VoiceTranslationResponseDto();

        response.text = text;

        return response;
    }
}
