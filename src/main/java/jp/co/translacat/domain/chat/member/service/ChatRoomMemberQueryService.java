package jp.co.translacat.domain.chat.member.service;

import jp.co.translacat.domain.chat.ai.dto.response.ChatAiDisplayMembersResponseDto;
import jp.co.translacat.domain.chat.ai.service.ChatAiDisplayMemberService;
import jp.co.translacat.domain.chat.language.dto.ChatLanguageSettingResult;
import jp.co.translacat.domain.chat.language.service.ChatLanguageSettingResolver;
import jp.co.translacat.domain.chat.member.dto.response.ChatRoomLanguageSettingResponseDto;
import jp.co.translacat.domain.chat.member.dto.response.ChatRoomMemberListResponseDto;
import jp.co.translacat.domain.chat.member.dto.response.ChatRoomMemberProfileResponseDto;
import jp.co.translacat.domain.chat.member.dto.response.ChatRoomMemberResponseDto;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import jp.co.translacat.domain.chat.presence.service.ChatPresenceQueryService;
import jp.co.translacat.domain.chat.presence.service.ChatPresenceVisibilityPolicy;
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
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomMemberQueryService {

    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatAiDisplayMemberService chatAiDisplayMemberService;
    private final ChatLanguageSettingResolver chatLanguageSettingResolver;
    private final UserProfileQueryService userProfileQueryService;
    private final FriendService friendService;
    private final FriendRequestRepository friendRequestRepository;
    private final UserBlockService userBlockService;
    private final ChatPresenceQueryService chatPresenceQueryService;
    private final ChatPresenceVisibilityPolicy chatPresenceVisibilityPolicy;

    public ChatRoomMemberListResponseDto getMembers(
            Long loginUserId,
            Long chatRoomId
    ) {
        ChatRoomMember currentMember =
                getActiveMember(loginUserId, chatRoomId);
        validateGeneralProfileApiAllowed(currentMember);

        List<ChatRoomMember> activeMembers = chatRoomMemberRepository
                .findByChatRoomIdAndActiveTrueAndDeletedAtIsNull(chatRoomId)
                .stream()
                .sorted(Comparator.comparing(ChatRoomMember::getJoinedAt))
                .toList();

        Map<Long, Boolean> onlineByUserId =
                chatPresenceVisibilityPolicy.isVisible(chatRoomId)
                        ? chatPresenceQueryService.resolveOnlineByUserIds(
                                activeMembers.stream()
                                        .map(ChatRoomMember::getUser)
                                        .map(User::getId)
                                        .toList()
                        )
                        : Map.of();

        List<ChatRoomMemberResponseDto> members = activeMembers.stream()
                .map(member -> ChatRoomMemberResponseDto.from(
                        member,
                        userProfileQueryService.getSummaryByUser(
                                member.getUser()
                        ),
                        onlineByUserId.get(member.getUser().getId())
                ))
                .toList();

        ChatAiDisplayMembersResponseDto aiMembers =
                chatAiDisplayMemberService.getDisplayMembers(chatRoomId);

        return ChatRoomMemberListResponseDto.of(
                members,
                aiMembers.members(),
                aiMembers.disclosureType()
        );
    }

    public ChatRoomMemberProfileResponseDto getMemberProfile(
            Long loginUserId,
            Long chatRoomId,
            Long targetUserId
    ) {
        ChatRoomMember currentMember =
                getActiveMember(loginUserId, chatRoomId);
        validateGeneralProfileApiAllowed(currentMember);

        ChatRoomMember targetMember =
                getActiveMember(targetUserId, chatRoomId);

        User targetUser = targetMember.getUser();

        UserSummaryProfileResponseDto profile =
                userProfileQueryService.getSummaryByUser(
                        targetUser
                );

        UserSearchFriendStatus friendStatus =
                UserFriendStatusResolver.resolve(
                        loginUserId,
                        targetUser,
                        userBlockService,
                        friendService,
                        friendRequestRepository
                );

        Boolean online = chatPresenceVisibilityPolicy.isVisible(chatRoomId)
                ? chatPresenceQueryService.resolveOnline(targetUserId)
                : null;

        return ChatRoomMemberProfileResponseDto.of(
                profile,
                friendStatus,
                online
        );
    }

    public ChatRoomLanguageSettingResponseDto getMyLanguageSetting(
            Long loginUserId,
            Long chatRoomId
    ) {
        ChatRoomMember chatRoomMember =
                getActiveMember(loginUserId, chatRoomId);

        ChatLanguageSettingResult languageSetting =
                chatLanguageSettingResolver.resolve(
                        chatRoomMember
                );

        return ChatRoomLanguageSettingResponseDto.from(
                chatRoomMember,
                languageSetting
        );
    }

    private void validateGeneralProfileApiAllowed(
            ChatRoomMember currentMember
    ) {
        if (currentMember.getChatRoom().getRoomType()
                == ChatRoomType.OPEN) {
            throw new BusinessException(
                    "OPEN 채팅방에서는 OPEN 전용 멤버 프로필 API를 사용해야 합니다.",
                    "OPEN_CHAT_MEMBER_PROFILE_API_REQUIRED"
            );
        }
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
