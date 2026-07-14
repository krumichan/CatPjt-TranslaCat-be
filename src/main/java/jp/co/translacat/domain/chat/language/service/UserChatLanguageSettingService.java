package jp.co.translacat.domain.chat.language.service;

import jp.co.translacat.domain.chat.language.dto.ChatLanguageSettingResult;
import jp.co.translacat.domain.chat.language.dto.ChatLanguageSettingUpdateRequestDto;
import jp.co.translacat.domain.chat.language.dto.UserChatLanguageSettingResponseDto;
import jp.co.translacat.domain.chat.language.entity.UserChatLanguageSetting;
import jp.co.translacat.domain.chat.language.enums.ChatLanguageSettingSource;
import jp.co.translacat.domain.chat.language.repository.UserChatLanguageSettingRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserChatLanguageSettingService {

    private final UserChatLanguageSettingRepository userChatLanguageSettingRepository;
    private final UserService userService;

    public UserChatLanguageSettingResponseDto getMyDefaultSetting(Long userId) {
        ChatLanguageSettingResult result = resolveDefault(userId);
        return UserChatLanguageSettingResponseDto.fromResult(userId, result);
    }

    @Transactional
    public UserChatLanguageSettingResponseDto updateMyDefaultSetting(
            Long userId,
            ChatLanguageSettingUpdateRequestDto request
    ) {
        if (request == null) {
            request = new ChatLanguageSettingUpdateRequestDto(
                    null,
                    null,
                    null,
                    null
            );
        }

        User user = userService.getById(userId);
        String originalLanguageCode = ChatLanguageSettingSupport.normalizeOrDefault(
                request.originalLanguageCode(),
                ChatLanguageSettingSupport.SYSTEM_DEFAULT_ORIGINAL_LANGUAGE_CODE
        );
        String translationLanguageCode = ChatLanguageSettingSupport.normalizeOrDefault(
                request.translationLanguageCode(),
                ChatLanguageSettingSupport.SYSTEM_DEFAULT_TRANSLATION_LANGUAGE_CODE
        );
        boolean showOriginal = ChatLanguageSettingSupport.showOriginalOrDefault(request);
        boolean showTranslation = ChatLanguageSettingSupport.showTranslationOrDefault(request);

        UserChatLanguageSetting setting = userChatLanguageSettingRepository
                .findByUserId(userId)
                .orElseGet(() -> UserChatLanguageSetting.create(
                        user,
                        originalLanguageCode,
                        translationLanguageCode,
                        showOriginal,
                        showTranslation
                ));

        setting.update(
                originalLanguageCode,
                translationLanguageCode,
                showOriginal,
                showTranslation
        );

        UserChatLanguageSetting savedSetting = userChatLanguageSettingRepository.save(setting);
        return UserChatLanguageSettingResponseDto.from(savedSetting);
    }

    public ChatLanguageSettingResult resolveDefault(Long userId) {
        return userChatLanguageSettingRepository
                .findByUserId(userId)
                .map(setting -> new ChatLanguageSettingResult(
                        setting.getOriginalLanguageCode(),
                        setting.getTranslationLanguageCode(),
                        setting.isShowOriginal(),
                        setting.isShowTranslation(),
                        false,
                        ChatLanguageSettingSource.DEFAULT
                ))
                .orElseGet(this::systemDefaultResult);
    }

    private ChatLanguageSettingResult systemDefaultResult() {
        return new ChatLanguageSettingResult(
                ChatLanguageSettingSupport.SYSTEM_DEFAULT_ORIGINAL_LANGUAGE_CODE,
                ChatLanguageSettingSupport.SYSTEM_DEFAULT_TRANSLATION_LANGUAGE_CODE,
                ChatLanguageSettingSupport.SYSTEM_DEFAULT_SHOW_ORIGINAL,
                ChatLanguageSettingSupport.SYSTEM_DEFAULT_SHOW_TRANSLATION,
                false,
                ChatLanguageSettingSource.SYSTEM
        );
    }
}
