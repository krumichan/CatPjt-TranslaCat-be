package jp.co.translacat.domain.chat.member.service;

import jp.co.translacat.domain.chat.language.dto.ChatLanguageSettingResult;
import jp.co.translacat.domain.chat.language.service.ChatLanguageSettingResolver;
import jp.co.translacat.domain.chat.member.dto.request.ChatRoomLanguageSettingUpdateRequestDto;
import jp.co.translacat.domain.chat.member.dto.response.ChatRoomLanguageSettingResponseDto;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatRoomMemberCommandService {

    private final ChatRoomMemberQueryService chatRoomMemberQueryService;
    private final ChatLanguageSettingResolver chatLanguageSettingResolver;

    public ChatRoomLanguageSettingResponseDto updateMyLanguageSetting(
            Long loginUserId,
            Long chatRoomId,
            ChatRoomLanguageSettingUpdateRequestDto request
    ) {
        validateLanguageSetting(request);

        ChatRoomMember chatRoomMember = chatRoomMemberQueryService.getActiveMember(
                loginUserId,
                chatRoomId
        );

        chatRoomMember.updateLanguageSetting(
                normalizeLanguageCode(request.originalLanguageCode()),
                normalizeLanguageCode(request.translationLanguageCode()),
                request.showOriginal(),
                request.showTranslation()
        );

        return toLanguageSettingResponse(chatRoomMember);
    }

    public ChatRoomLanguageSettingResponseDto resetMyLanguageSetting(
            Long loginUserId,
            Long chatRoomId
    ) {
        ChatRoomMember chatRoomMember = chatRoomMemberQueryService.getActiveMember(
                loginUserId,
                chatRoomId
        );

        chatRoomMember.resetLanguageSetting();

        return toLanguageSettingResponse(chatRoomMember);
    }

    private void validateLanguageSetting(ChatRoomLanguageSettingUpdateRequestDto request) {
        if (!request.showOriginal() && !request.showTranslation()) {
            throw new BusinessException("원문 또는 번역 중 최소 하나는 표시해야 합니다.");
        }
    }

    private ChatRoomLanguageSettingResponseDto toLanguageSettingResponse(
            ChatRoomMember chatRoomMember
    ) {
        ChatLanguageSettingResult languageSetting =
                chatLanguageSettingResolver.resolve(chatRoomMember);

        return ChatRoomLanguageSettingResponseDto.from(
                chatRoomMember,
                languageSetting
        );
    }

    private String normalizeLanguageCode(String languageCode) {
        return languageCode == null ? null : languageCode.trim();
    }
}