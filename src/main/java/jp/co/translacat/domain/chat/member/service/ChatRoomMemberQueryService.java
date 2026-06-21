package jp.co.translacat.domain.chat.member.service;

import jp.co.translacat.domain.chat.language.dto.ChatLanguageSettingResult;
import jp.co.translacat.domain.chat.language.service.ChatLanguageSettingResolver;
import jp.co.translacat.domain.chat.member.dto.response.ChatRoomLanguageSettingResponseDto;
import jp.co.translacat.domain.chat.member.dto.response.ChatRoomMemberListResponseDto;
import jp.co.translacat.domain.chat.member.dto.response.ChatRoomMemberResponseDto;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomMemberQueryService {

    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatLanguageSettingResolver chatLanguageSettingResolver;

    public ChatRoomMemberListResponseDto getMembers(
            Long loginUserId,
            Long chatRoomId
    ) {
        // 접근 권한 확인
        getActiveMember(loginUserId, chatRoomId);

        List<ChatRoomMemberResponseDto> members = chatRoomMemberRepository
                .findByChatRoomIdAndActiveTrueAndDeletedAtIsNull(chatRoomId)
                .stream()
                .sorted(Comparator.comparing(ChatRoomMember::getJoinedAt))
                .map(ChatRoomMemberResponseDto::from)
                .toList();

        return ChatRoomMemberListResponseDto.from(members);
    }

    public ChatRoomLanguageSettingResponseDto getMyLanguageSetting(
            Long loginUserId,
            Long chatRoomId
    ) {
        ChatRoomMember chatRoomMember = getActiveMember(loginUserId, chatRoomId);

        ChatLanguageSettingResult languageSetting =
                chatLanguageSettingResolver.resolve(chatRoomMember);

        return ChatRoomLanguageSettingResponseDto.from(
                chatRoomMember,
                languageSetting
        );
    }

    public ChatRoomMember getActiveMember(
            Long userId,
            Long chatRoomId
    ) {
        return chatRoomMemberRepository
                .findByChatRoomIdAndUserIdAndActiveTrueAndDeletedAtIsNull(
                        chatRoomId,
                        userId
                )
                .orElseThrow(() -> new BusinessException("채팅방 멤버가 아니거나 접근 권한이 없습니다."));
    }
}