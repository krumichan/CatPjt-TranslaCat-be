package jp.co.translacat.domain.chat.language.service;

import jp.co.translacat.domain.chat.language.dto.ChatLanguageSettingResult;
import jp.co.translacat.domain.chat.language.dto.ChatLanguageSettingUpdateRequestDto;
import jp.co.translacat.domain.chat.language.dto.ChatRoomLanguageSettingResponseDto;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomLanguageSettingService {

    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatLanguageSettingResolver chatLanguageSettingResolver;

    public ChatRoomLanguageSettingResponseDto getMyRoomSetting(
            Long loginUserId,
            Long chatRoomId
    ) {
        ChatRoomMember chatRoomMember = getMyActiveMember(loginUserId, chatRoomId);
        ChatLanguageSettingResult result = chatLanguageSettingResolver.resolve(chatRoomMember);
        return ChatRoomLanguageSettingResponseDto.from(
                chatRoomId,
                loginUserId,
                result
        );
    }

    @Transactional
    public ChatRoomLanguageSettingResponseDto updateMyRoomSetting(
            Long loginUserId,
            Long chatRoomId,
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

        ChatRoomMember chatRoomMember = getMyActiveMember(loginUserId, chatRoomId);
        ChatLanguageSettingResult defaultResult = chatLanguageSettingResolver.resolve(chatRoomMember);

        String originalLanguageCode = ChatLanguageSettingSupport.normalizeOrDefault(
                request.originalLanguageCode(),
                defaultResult.originalLanguageCode()
        );
        String translationLanguageCode = ChatLanguageSettingSupport.normalizeOrDefault(
                request.translationLanguageCode(),
                defaultResult.translationLanguageCode()
        );
        boolean showOriginal = request.showOriginal() == null
                ? defaultResult.showOriginal()
                : request.showOriginal();
        boolean showTranslation = request.showTranslation() == null
                ? defaultResult.showTranslation()
                : request.showTranslation();

        chatRoomMember.updateLanguageSetting(
                originalLanguageCode,
                translationLanguageCode,
                showOriginal,
                showTranslation
        );

        return getMyRoomSetting(loginUserId, chatRoomId);
    }

    @Transactional
    public ChatRoomLanguageSettingResponseDto resetMyRoomSetting(
            Long loginUserId,
            Long chatRoomId
    ) {
        ChatRoomMember chatRoomMember = getMyActiveMember(loginUserId, chatRoomId);
        chatRoomMember.resetLanguageSetting();
        return getMyRoomSetting(loginUserId, chatRoomId);
    }

    private ChatRoomMember getMyActiveMember(
            Long loginUserId,
            Long chatRoomId
    ) {
        return chatRoomMemberRepository
                .findByChatRoomIdAndUserIdAndActiveTrueAndDeletedAtIsNull(
                        chatRoomId,
                        loginUserId
                )
                .orElseThrow(() -> new BusinessException(
                        "채팅방 접근 권한이 없습니다.",
                        "CHAT_ROOM_ACCESS_DENIED"
                ));
    }
}
