package jp.co.translacat.domain.chat.openchat.service;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.repository.ChatMessageRepository;
import jp.co.translacat.domain.chat.openchat.ban.entity.OpenChatBan;
import jp.co.translacat.domain.chat.openchat.ban.repository.OpenChatBanRepository;
import jp.co.translacat.domain.chat.openchat.dto.request.OpenChatBanCreateRequestDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatBanActionResponseDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatMemberProfileResponseDto;
import jp.co.translacat.domain.chat.openchat.entity.OpenChatRoom;
import jp.co.translacat.domain.chat.openchat.event.OpenChatMemberBannedApplicationEvent;
import jp.co.translacat.domain.chat.openchat.event.OpenChatMemberRoleUpdatedApplicationEvent;
import jp.co.translacat.domain.chat.openchat.profile.entity.OpenChatMemberProfile;
import jp.co.translacat.domain.chat.openchat.profile.repository.OpenChatMemberProfileRepository;
import jp.co.translacat.domain.chat.openchat.profile.service.OpenChatProfileResponseMapper;
import jp.co.translacat.domain.chat.openchat.repository.OpenChatRoomRepository;
import jp.co.translacat.domain.chat.openchat.support.OpenChatErrorCode;
import jp.co.translacat.domain.chat.openchat.support.OpenChatPolicy;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class OpenChatModerationService {

    private final OpenChatRoomRepository openChatRoomRepository;
    private final ChatRoomMemberRepository memberRepository;
    private final OpenChatMemberProfileRepository profileRepository;
    private final OpenChatBanRepository banRepository;
    private final ChatMessageRepository messageRepository;
    private final OpenChatProfileResponseMapper profileResponseMapper;
    private final ApplicationEventPublisher eventPublisher;

    public OpenChatMemberProfileResponseDto assignAdmin(
            Long loginUserId,
            Long roomId,
            Long targetOpenChatMemberId
    ) {
        OpenChatRoom openChatRoom = getLockedActiveRoom(roomId);
        ChatRoomMember actor = getLockedActiveActor(
                loginUserId,
                roomId
        );
        validateOwner(actor);

        ChatRoomMember target = getLockedActiveTarget(
                targetOpenChatMemberId,
                roomId
        );
        validateAdminAssignmentTarget(actor, target, roomId);

        target.changeRole(ChatRoomMemberRole.ADMIN);
        memberRepository.flush();

        eventPublisher.publishEvent(
                OpenChatMemberRoleUpdatedApplicationEvent.of(
                        openChatRoom.getChatRoom().getId(),
                        target.getId(),
                        target.getRole()
                )
        );

        return profileResponseMapper.toResponse(
                getProfile(target.getId())
        );
    }

    public OpenChatMemberProfileResponseDto revokeAdmin(
            Long loginUserId,
            Long roomId,
            Long targetOpenChatMemberId
    ) {
        OpenChatRoom openChatRoom = getLockedActiveRoom(roomId);
        ChatRoomMember actor = getLockedActiveActor(
                loginUserId,
                roomId
        );
        validateOwner(actor);

        ChatRoomMember target = getLockedActiveTarget(
                targetOpenChatMemberId,
                roomId
        );
        if (!target.isAdmin()) {
            throw new BusinessException(
                    "활성 ADMIN 멤버만 ADMIN 역할을 해제할 수 있습니다.",
                    OpenChatErrorCode.ADMIN_TARGET_INVALID
            );
        }

        target.changeRole(ChatRoomMemberRole.MEMBER);
        memberRepository.flush();

        eventPublisher.publishEvent(
                OpenChatMemberRoleUpdatedApplicationEvent.of(
                        openChatRoom.getChatRoom().getId(),
                        target.getId(),
                        target.getRole()
                )
        );

        return profileResponseMapper.toResponse(
                getProfile(target.getId())
        );
    }

    public OpenChatBanActionResponseDto ban(
            Long loginUserId,
            Long roomId,
            OpenChatBanCreateRequestDto request
    ) {
        validateBanRequest(request);
        OpenChatRoom openChatRoom = getLockedActiveRoom(roomId);
        ChatRoomMember actor = getLockedActiveActor(
                loginUserId,
                roomId
        );
        validateModerator(actor);

        ChatRoomMember target = getLockedActiveTarget(
                request.targetOpenChatMemberId(),
                roomId
        );
        validateBanTarget(actor, target);

        if (banRepository
                .findActiveByRoomIdAndTargetUserIdForUpdate(
                        roomId,
                        target.getUser().getId()
                )
                .isPresent()) {
            throw new BusinessException(
                    "이미 해당 OPEN 채팅방에서 차단된 사용자입니다.",
                    OpenChatErrorCode.BAN_ALREADY_ACTIVE
            );
        }

        OpenChatMemberProfile targetProfile =
                getProfile(target.getId());
        ChatRoomMemberRole targetRoleSnapshot = target.getRole();
        String reason = normalizeReason(request.reason());

        OpenChatBan ban = OpenChatBan.create(
                openChatRoom.getChatRoom(),
                target.getUser(),
                target,
                targetProfile.getMemberCode(),
                targetProfile.getNickname(),
                targetProfile.getProfileImageObjectKey(),
                target.getJoinedAt(),
                targetRoleSnapshot,
                actor,
                actor.getRole(),
                reason
        );
        OpenChatBan savedBan = banRepository.save(ban);

        target.banFromOpenChat();

        ChatMessage systemMessage = ChatMessage.createSystemMessage(
                openChatRoom.getChatRoom(),
                "OPEN 멤버 "
                        + targetProfile.getMemberCode()
                        + "님이 운영 정책에 따라 강제 퇴장되었습니다."
        );
        messageRepository.save(systemMessage);

        memberRepository.flush();
        banRepository.flush();
        messageRepository.flush();

        eventPublisher.publishEvent(
                OpenChatMemberBannedApplicationEvent.of(
                        roomId,
                        target.getId(),
                        target.getUser().getEmail(),
                        reason,
                        savedBan.getBannedAt()
                )
        );

        return new OpenChatBanActionResponseDto(
                roomId,
                savedBan.getId(),
                target.getId(),
                true,
                savedBan.getBannedAt(),
                null
        );
    }

    public OpenChatBanActionResponseDto release(
            Long loginUserId,
            Long roomId,
            Long banId
    ) {
        getLockedActiveRoom(roomId);
        ChatRoomMember actor = getLockedActiveActor(
                loginUserId,
                roomId
        );
        validateModerator(actor);

        OpenChatBan ban = banRepository
                .findActiveByIdAndRoomIdForUpdate(
                        banId,
                        roomId
                )
                .orElseThrow(() -> new BusinessException(
                        "활성 OPEN 채팅 차단 이력을 찾을 수 없습니다.",
                        OpenChatErrorCode.BAN_NOT_FOUND
                ));

        validateReleaseAllowed(actor, ban);
        ban.release(actor);
        banRepository.flush();

        return new OpenChatBanActionResponseDto(
                roomId,
                ban.getId(),
                ban.getTargetChatRoomMember().getId(),
                false,
                ban.getBannedAt(),
                ban.getReleasedAt()
        );
    }

    private OpenChatRoom getLockedActiveRoom(Long roomId) {
        if (roomId == null || roomId <= 0) {
            throw roomNotFound();
        }

        OpenChatRoom room = openChatRoomRepository
                .findByChatRoomIdForUpdate(roomId)
                .orElseThrow(this::roomNotFound);

        if (room.isClosed()) {
            throw new BusinessException(
                    "종료된 OPEN 채팅방에서는 운영 작업을 수행할 수 없습니다.",
                    OpenChatErrorCode.ROOM_CLOSED
            );
        }
        return room;
    }

    private ChatRoomMember getLockedActiveActor(
            Long userId,
            Long roomId
    ) {
        return memberRepository
                .findActiveByRoomIdAndUserIdForUpdate(
                        roomId,
                        userId
                )
                .orElseThrow(() -> new BusinessException(
                        "OPEN 채팅방 운영 권한이 없습니다.",
                        OpenChatErrorCode.MODERATION_ACCESS_DENIED
                ));
    }

    private ChatRoomMember getLockedActiveTarget(
            Long targetOpenChatMemberId,
            Long roomId
    ) {
        if (targetOpenChatMemberId == null
                || targetOpenChatMemberId <= 0) {
            throw new BusinessException(
                    "대상 OPEN 멤버 ID는 필수입니다.",
                    OpenChatErrorCode.BAN_TARGET_REQUIRED
            );
        }
        return memberRepository
                .findActiveByIdAndRoomIdForUpdate(
                        targetOpenChatMemberId,
                        roomId
                )
                .orElseThrow(() -> new BusinessException(
                        "활성 OPEN 멤버를 찾을 수 없습니다.",
                        OpenChatErrorCode.BAN_TARGET_INVALID
                ));
    }

    private OpenChatMemberProfile getProfile(Long memberId) {
        return profileRepository.findByChatRoomMemberId(memberId)
                .orElseThrow(() -> new BusinessException(
                        "OPEN 채팅 프로필을 찾을 수 없습니다.",
                        OpenChatErrorCode.PROFILE_NOT_FOUND
                ));
    }

    private void validateOwner(ChatRoomMember actor) {
        if (!actor.isOwner()) {
            throw new BusinessException(
                    "OPEN 채팅방 OWNER만 수행할 수 있습니다.",
                    OpenChatErrorCode.OWNER_ONLY
            );
        }
    }

    private void validateModerator(ChatRoomMember actor) {
        if (!actor.isOwner() && !actor.isAdmin()) {
            throw new BusinessException(
                    "OPEN 채팅방 OWNER 또는 ADMIN만 수행할 수 있습니다.",
                    OpenChatErrorCode.MODERATION_ACCESS_DENIED
            );
        }
    }

    private void validateAdminAssignmentTarget(
            ChatRoomMember actor,
            ChatRoomMember target,
            Long roomId
    ) {
        if (actor.getId().equals(target.getId())
                || !target.isMember()
                || banRepository.existsActiveByRoomIdAndTargetUserId(
                        roomId,
                        target.getUser().getId()
                )) {
            throw new BusinessException(
                    "활성 MEMBER만 ADMIN으로 지정할 수 있습니다.",
                    OpenChatErrorCode.ADMIN_TARGET_INVALID
            );
        }
    }

    private void validateBanTarget(
            ChatRoomMember actor,
            ChatRoomMember target
    ) {
        if (actor.getId().equals(target.getId())) {
            throw new BusinessException(
                    "자기 자신을 강제 퇴장시킬 수 없습니다.",
                    OpenChatErrorCode.BAN_SELF_NOT_ALLOWED
            );
        }
        if (target.isOwner()) {
            throw new BusinessException(
                    "OWNER는 강제 퇴장 대상이 될 수 없습니다.",
                    OpenChatErrorCode.BAN_ROLE_FORBIDDEN
            );
        }
        if (actor.isAdmin() && !target.isMember()) {
            throw new BusinessException(
                    "ADMIN은 MEMBER만 강제 퇴장시킬 수 있습니다.",
                    OpenChatErrorCode.BAN_ROLE_FORBIDDEN
            );
        }
    }

    private void validateReleaseAllowed(
            ChatRoomMember actor,
            OpenChatBan ban
    ) {
        if (actor.isOwner()) {
            return;
        }
        if (!actor.isAdmin()
                || ban.getBannedByRole() != ChatRoomMemberRole.ADMIN
                || ban.getTargetRoleSnapshot()
                != ChatRoomMemberRole.MEMBER) {
            throw new BusinessException(
                    "해당 차단 이력을 해제할 권한이 없습니다.",
                    OpenChatErrorCode.BAN_RELEASE_FORBIDDEN
            );
        }
    }

    private void validateBanRequest(OpenChatBanCreateRequestDto request) {
        if (request == null) {
            throw new BusinessException(
                    "강제 퇴장 요청은 필수입니다.",
                    OpenChatErrorCode.BAN_REQUEST_REQUIRED
            );
        }
        if (request.targetOpenChatMemberId() == null
                || request.targetOpenChatMemberId() <= 0) {
            throw new BusinessException(
                    "강제 퇴장 대상 OPEN 멤버 ID는 필수입니다.",
                    OpenChatErrorCode.BAN_TARGET_REQUIRED
            );
        }
        normalizeReason(request.reason());
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(
                    "강제 퇴장 사유는 필수입니다.",
                    OpenChatErrorCode.BAN_REASON_REQUIRED
            );
        }
        String normalized = reason.trim();
        if (normalized.length()
                > OpenChatPolicy.MAX_BAN_REASON_LENGTH) {
            throw new BusinessException(
                    "강제 퇴장 사유는 500자 이하여야 합니다.",
                    OpenChatErrorCode.BAN_REASON_TOO_LONG
            );
        }
        return normalized;
    }

    private BusinessException roomNotFound() {
        return new BusinessException(
                "OPEN 채팅방을 찾을 수 없습니다.",
                OpenChatErrorCode.ROOM_NOT_FOUND
        );
    }
}
