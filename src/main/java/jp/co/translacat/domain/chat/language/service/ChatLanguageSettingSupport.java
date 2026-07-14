package jp.co.translacat.domain.chat.language.service;

import jp.co.translacat.domain.chat.language.dto.ChatLanguageSettingUpdateRequestDto;

final class ChatLanguageSettingSupport {

    static final String SYSTEM_DEFAULT_ORIGINAL_LANGUAGE_CODE = "ko";
    static final String SYSTEM_DEFAULT_TRANSLATION_LANGUAGE_CODE = "ja";
    static final boolean SYSTEM_DEFAULT_SHOW_ORIGINAL = true;
    static final boolean SYSTEM_DEFAULT_SHOW_TRANSLATION = true;

    private ChatLanguageSettingSupport() {
    }

    static String normalizeOrDefault(
            String languageCode,
            String defaultLanguageCode
    ) {
        String normalized = normalize(languageCode);
        return normalized == null ? defaultLanguageCode : normalized;
    }

    static String normalize(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return null;
        }
        return languageCode.trim().toLowerCase();
    }

    static boolean showOriginalOrDefault(
            ChatLanguageSettingUpdateRequestDto request
    ) {
        return request.showOriginal() == null
                ? SYSTEM_DEFAULT_SHOW_ORIGINAL
                : request.showOriginal();
    }

    static boolean showTranslationOrDefault(
            ChatLanguageSettingUpdateRequestDto request
    ) {
        return request.showTranslation() == null
                ? SYSTEM_DEFAULT_SHOW_TRANSLATION
                : request.showTranslation();
    }
}
