package jp.co.translacat.domain.chat.openchat.profile.service;

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
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OpenChatProfileService {

    private final OpenChatAccessService accessService;
    private final OpenChatMemberProfileRepository profileRepository;
    private final OpenChatProfileResponseMapper responseMapper;
    private final OpenChatProfileValidator profileValidator;
    private final ApplicationEventPublisher eventPublisher;

    public OpenChatMemberProfileResponseDto getMyProfile(
            Long loginUserId,
            Long roomId
    ) {
        ChatRoomMember member = accessService.getActiveOpenMember(
                loginUserId,
                roomId
        );
        return responseMapper.toResponse(getProfile(member.getId()));
    }

    public OpenChatMemberListResponseDto getMembers(
            Long loginUserId,
            Long roomId
    ) {
        accessService.getActiveOpenMember(loginUserId, roomId);

        List<OpenChatMemberProfileResponseDto> members = profileRepository
                .findByChatRoomMemberChatRoomIdAndChatRoomMemberActiveTrueAndChatRoomMemberDeletedAtIsNullOrderByChatRoomMemberJoinedAtAsc(
                        roomId
                )
                .stream()
                .map(responseMapper::toResponse)
                .toList();

        return OpenChatMemberListResponseDto.of(members);
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

        return responseMapper.toResponse(profile);
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
