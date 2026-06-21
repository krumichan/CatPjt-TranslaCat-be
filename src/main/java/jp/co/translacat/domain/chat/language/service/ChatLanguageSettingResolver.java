package jp.co.translacat.domain.chat.language.service;

import jp.co.translacat.domain.chat.language.dto.ChatLanguageSettingResult;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import org.springframework.stereotype.Component;

@Component
public class ChatLanguageSettingResolver {

    private static final String SYSTEM_DEFAULT_ORIGINAL_LANGUAGE_CODE = "ko";
    private static final String SYSTEM_DEFAULT_TRANSLATION_LANGUAGE_CODE = "ja";

    public ChatLanguageSettingResult resolve(ChatRoomMember chatRoomMember) {
        // TODO: 사용자 기본 언어 설정 도입 후, User/Profile/Setting 값으로 fallback 변경
        String defaultOriginalLanguageCode = SYSTEM_DEFAULT_ORIGINAL_LANGUAGE_CODE;
        String defaultTranslationLanguageCode = SYSTEM_DEFAULT_TRANSLATION_LANGUAGE_CODE;

        String effectiveOriginalLanguageCode =
                chatRoomMember.getOriginalLanguageCode() != null
                        ? chatRoomMember.getOriginalLanguageCode()
                        : defaultOriginalLanguageCode;

        String effectiveTranslationLanguageCode =
                chatRoomMember.getTranslationLanguageCode() != null
                        ? chatRoomMember.getTranslationLanguageCode()
                        : defaultTranslationLanguageCode;

        boolean roomLanguageSettingApplied = chatRoomMember.hasRoomLanguageSetting();

        return new ChatLanguageSettingResult(
                effectiveOriginalLanguageCode,
                effectiveTranslationLanguageCode,
                roomLanguageSettingApplied
        );
    }
}