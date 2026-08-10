package jp.co.translacat.domain.chat.openchat.profile.service;

import jp.co.translacat.domain.chat.ai.dto.response.ChatAiDisplayMembersResponseDto;
import jp.co.translacat.domain.chat.ai.service.ChatAiDisplayMemberService;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.openchat.dto.request.OpenChatProfileUpdateRequestDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatMemberListResponseDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatMemberProfileResponseDto;
import jp.co.translacat.domain.chat.openchat.event.OpenChatProfileUpdatedApplicationEvent;
import jp.co.translacat.domain.chat.openchat.profile.entity.OpenChatMemberProfile;
import jp.co.translacat.domain.chat.openchat.profile.repository.OpenChatMemberProfileRepository;
import jp.co.translacat.domain.chat.openchat.service.OpenChatAccessService;
import jp.co.translacat.domain.chat.openchat.support.OpenChatErrorCode;
import jp.co.translacat.domain.chat.openchat.support.OpenChatProfileValidator;
import jp.co.translacat.domain.chat.presence.service.ChatPresenceQueryService;
import jp.co.translacat.domain.chat.presence.service.ChatPresenceVisibilityPolicy;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OpenChatProfileService {

    private final OpenChatAccessService accessService;
    private final ChatAiDisplayMemberService chatAiDisplayMemberService;
    private final OpenChatMemberProfileRepository profileRepository;
    private final OpenChatProfileResponseMapper responseMapper;
    private final OpenChatProfileValidator profileValidator;
    private final ApplicationEventPublisher eventPublisher;
    private final ChatPresenceQueryService chatPresenceQueryService;
    private final ChatPresenceVisibilityPolicy chatPresenceVisibilityPolicy;

    public OpenChatMemberProfileResponseDto getMyProfile(
            Long loginUserId,
            Long roomId
    ) {
        ChatRoomMember member = accessService.getActiveOpenMember(
                loginUserId,
                roomId
        );
        OpenChatMemberProfile profile = getProfile(member.getId());
        return responseMapper.toResponse(
                profile,
                resolveOnline(roomId, member)
        );
    }

    public OpenChatMemberListResponseDto getMembers(
            Long loginUserId,
            Long roomId
    ) {
        accessService.getActiveOpenMember(loginUserId, roomId);

        List<OpenChatMemberProfile> profiles = profileRepository
                .findByChatRoomMemberChatRoomIdAndChatRoomMemberActiveTrueAndChatRoomMemberDeletedAtIsNullOrderByChatRoomMemberJoinedAtAsc(
                        roomId
                );

        Map<Long, Boolean> onlineByUserId =
                chatPresenceVisibilityPolicy.isVisible(roomId)
                        ? chatPresenceQueryService.resolveOnlineByUserIds(
                                profiles.stream()
                                        .map(OpenChatMemberProfile::getChatRoomMember)
                                        .map(ChatRoomMember::getUser)
                                        .map(user -> user.getId())
                                        .toList()
                        )
                        : Map.of();

        List<OpenChatMemberProfileResponseDto> members = profiles.stream()
                .map(profile -> responseMapper.toResponse(
                        profile,
                        onlineByUserId.get(
                                profile.getChatRoomMember().getUser().getId()
                        )
                ))
                .toList();

        ChatAiDisplayMembersResponseDto aiMembers =
                chatAiDisplayMemberService.getDisplayMembers(roomId);

        return OpenChatMemberListResponseDto.of(
                members,
                aiMembers.members(),
                aiMembers.disclosureType()
        );
    }

    public OpenChatMemberProfileResponseDto getMemberProfile(
            Long loginUserId,
            Long roomId,
            Long openChatMemberId
    ) {
        accessService.getActiveOpenMember(loginUserId, roomId);

        OpenChatMemberProfile profile = profileRepository
                .findByChatRoomMemberIdAndChatRoomMemberChatRoomId(
                        openChatMemberId,
                        roomId
                )
                .filter(value -> value.getChatRoomMember().isActive())
                .filter(value -> !value.getChatRoomMember().isDeleted())
                .orElseThrow(() -> new BusinessException(
                        "OPEN 채팅 멤버 프로필을 찾을 수 없습니다.",
                        OpenChatErrorCode.MEMBER_NOT_FOUND
                ));

        return responseMapper.toResponse(
                profile,
                resolveOnline(roomId, profile.getChatRoomMember())
        );
    }

    @Transactional
    public OpenChatMemberProfileResponseDto updateMyProfile(
            Long loginUserId,
            Long roomId,
            OpenChatProfileUpdateRequestDto request
    ) {
        accessService.validateProfileEditAllowed(loginUserId, roomId);
        ChatRoomMember member = accessService.getActiveOpenMember(
                loginUserId,
                roomId
        );
        OpenChatMemberProfile profile = getProfile(member.getId());

        String nickname = profileValidator.normalizeNickname(
                request != null ? request.nickname() : null
        );
        profile.updateNickname(nickname);
        publishProfileUpdated(profile);

        return responseMapper.toResponse(profile);
    }


    private Boolean resolveOnline(
            Long roomId,
            ChatRoomMember member
    ) {
        if (member == null
                || member.getUser() == null
                || !chatPresenceVisibilityPolicy.isVisible(roomId)) {
            return null;
        }
        return chatPresenceQueryService.resolveOnline(
                member.getUser().getId()
        );
    }

    private OpenChatMemberProfile getProfile(Long memberId) {
        return profileRepository.findByChatRoomMemberId(memberId)
                .orElseThrow(() -> new BusinessException(
                        "OPEN 채팅 프로필을 찾을 수 없습니다.",
                        OpenChatErrorCode.PROFILE_NOT_FOUND
                ));
    }

    private void publishProfileUpdated(
            OpenChatMemberProfile profile
    ) {
        ChatRoomMember member = profile.getChatRoomMember();
        eventPublisher.publishEvent(
                OpenChatProfileUpdatedApplicationEvent.of(
                        member.getChatRoom().getId(),
                        member.getId(),
                        profile.getMemberCode(),
                        profile.getNickname(),
                        profile.getProfileImageObjectKey(),
                        member.getRole()
                )
        );
    }
}
