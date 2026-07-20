package jp.co.translacat.domain.chat.member.service;

import jp.co.translacat.domain.chat.language.dto.ChatLanguageSettingResult;
import jp.co.translacat.domain.chat.language.service.ChatLanguageSettingResolver;
import jp.co.translacat.domain.chat.member.dto.response.ChatRoomLanguageSettingResponseDto;
import jp.co.translacat.domain.chat.member.dto.response.ChatRoomMemberListResponseDto;
import jp.co.translacat.domain.chat.member.dto.response.ChatRoomMemberProfileResponseDto;
import jp.co.translacat.domain.chat.member.dto.response.ChatRoomMemberResponseDto;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.user.block.service.UserBlockService;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.friend.request.repository.FriendRequestRepository;
import jp.co.translacat.domain.user.friend.service.FriendService;
import jp.co.translacat.domain.user.profile.dto.UserSummaryProfileResponseDto;
import jp.co.translacat.domain.user.profile.service.UserProfileQueryService;
import jp.co.translacat.domain.user.search.enums.UserSearchFriendStatus;
import jp.co.translacat.domain.user.search.service.UserFriendStatusResolver;
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
    private final UserProfileQueryService userProfileQueryService;
    private final FriendService friendService;
    private final FriendRequestRepository friendRequestRepository;
    private final UserBlockService userBlockService;

    public ChatRoomMemberListResponseDto getMembers(
            Long loginUserId,
            Long chatRoomId
    ) {
        getActiveMember(loginUserId, chatRoomId);

        List<ChatRoomMemberResponseDto> members = chatRoomMemberRepository
                .findByChatRoomIdAndActiveTrueAndDeletedAtIsNull(chatRoomId)
                .stream()
                .sorted(Comparator.comparing(ChatRoomMember::getJoinedAt))
                .map(ChatRoomMemberResponseDto::from)
                .toList();

        return ChatRoomMemberListResponseDto.from(members);
    }

    public ChatRoomMemberProfileResponseDto getMemberProfile(
            Long loginUserId,
            Long chatRoomId,
            Long targetUserId
    ) {
        getActiveMember(loginUserId, chatRoomId);

        ChatRoomMember targetMember =
                getActiveMember(targetUserId, chatRoomId);

        User targetUser = targetMember.getUser();

        UserSummaryProfileResponseDto profile =
                userProfileQueryService.getSummaryByUser(targetUser);

        UserSearchFriendStatus friendStatus =
                UserFriendStatusResolver.resolve(
                        loginUserId,
                        targetUser,
                        userBlockService,
                        friendService,
                        friendRequestRepository
                );

        return ChatRoomMemberProfileResponseDto.of(
                profile,
                friendStatus
        );
    }

    public ChatRoomLanguageSettingResponseDto getMyLanguageSetting(
            Long loginUserId,
            Long chatRoomId
    ) {
        ChatRoomMember chatRoomMember =
                getActiveMember(loginUserId, chatRoomId);

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
                .orElseThrow(() -> new BusinessException(
                        "채팅방 멤버가 아니거나 접근 권한이 없습니다.",
                        "CHAT_ROOM_MEMBER_ACCESS_DENIED"
                ));
    }
}
