package jp.co.translacat.domain.chat.openchat.service;

import jp.co.translacat.domain.chat.language.dto.ChatLanguageSettingResult;
import jp.co.translacat.domain.chat.language.service.UserChatLanguageSettingService;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.enums.ChatMessageStatus;
import jp.co.translacat.domain.chat.message.repository.ChatMessageRepository;
import jp.co.translacat.domain.chat.openchat.dto.request.OpenChatJoinRequestDto;
import jp.co.translacat.domain.chat.openchat.dto.request.OpenChatOwnerTransferRequestDto;
import jp.co.translacat.domain.chat.openchat.dto.request.OpenChatProfileRequestDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatMembershipResponseDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatRoomDetailResponseDto;
import jp.co.translacat.domain.chat.openchat.entity.OpenChatRoom;
import jp.co.translacat.domain.chat.openchat.event.OpenChatProfileUpdatedApplicationEvent;
import jp.co.translacat.domain.chat.openchat.event.OpenChatRoomClosedApplicationEvent;
import jp.co.translacat.domain.chat.openchat.profile.entity.OpenChatMemberProfile;
import jp.co.translacat.domain.chat.openchat.profile.repository.OpenChatMemberProfileRepository;
import jp.co.translacat.domain.chat.openchat.profile.service.OpenChatProfileResponseMapper;
import jp.co.translacat.domain.chat.openchat.repository.OpenChatRoomRepository;
import jp.co.translacat.domain.chat.openchat.support.OpenChatErrorCode;
import jp.co.translacat.domain.chat.openchat.support.OpenChatMemberCodeGenerator;
import jp.co.translacat.domain.chat.openchat.support.OpenChatProfileValidator;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.repository.ChatRoomRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.service.UserService;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class OpenChatMembershipService {

    private final OpenChatRoomRepository openChatRoomRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository memberRepository;
    private final OpenChatMemberProfileRepository profileRepository;
    private final ChatMessageRepository messageRepository;
    private final UserService userService;
    private final UserChatLanguageSettingService languageSettingService;
    private final OpenChatMemberCodeGenerator memberCodeGenerator;
    private final OpenChatProfileValidator profileValidator;
    private final OpenChatProfileResponseMapper profileResponseMapper;
    private final OpenChatRoomQueryService roomQueryService;
    private final ApplicationEventPublisher eventPublisher;

    public OpenChatRoomDetailResponseDto join(
            Long loginUserId,
            Long roomId,
            OpenChatJoinRequestDto request
    ) {
        OpenChatRoom openChatRoom = getLockedRoom(roomId);
        ChatRoom chatRoom = openChatRoom.getChatRoom();

        validateJoinable(openChatRoom);

        Optional<ChatRoomMember> existingMember = memberRepository
                .findByChatRoomIdAndUserId(roomId, loginUserId);

        if (existingMember.filter(ChatRoomMember::isActive).isPresent()) {
            return roomQueryService.getDetail(loginUserId, roomId);
        }

        long activeMemberCount = memberRepository
                .countByChatRoomIdAndActiveTrueAndDeletedAtIsNull(roomId);
        if (activeMemberCount >= openChatRoom.getMaxMemberCount()) {
            throw new BusinessException(
                    "OPEN 채팅방 정원이 가득 찼습니다.",
                    OpenChatErrorCode.ROOM_FULL
            );
        }

        Long latestMessageId = messageRepository
                .findTopByChatRoomIdAndStatusAndDeletedAtIsNullOrderByIdDesc(
                        roomId,
                        ChatMessageStatus.SENT
                )
                .map(ChatMessage::getId)
                .orElse(null);

        ChatLanguageSettingResult languageSetting =
                languageSettingService.resolveDefault(loginUserId);

        if (existingMember.isPresent()) {
            restoreMember(
                    existingMember.get(),
                    request,
                    languageSetting,
                    latestMessageId
            );
        } else {
            createMember(
                    loginUserId,
                    chatRoom,
                    request,
                    languageSetting,
                    latestMessageId
            );
        }

        memberRepository.flush();
        profileRepository.flush();

        return roomQueryService.getDetail(loginUserId, roomId);
    }

    public OpenChatMembershipResponseDto leave(
            Long loginUserId,
            Long roomId
    ) {
        OpenChatRoom openChatRoom = getLockedRoom(roomId);
        ChatRoomMember member = getActiveMember(
                loginUserId,
                roomId
        );

        if (member.isOwner()) {
            long activeMemberCount = memberRepository
                    .countByChatRoomIdAndActiveTrueAndDeletedAtIsNull(
                            roomId
                    );
            if (activeMemberCount > 1) {
                throw new BusinessException(
                        "OWNER는 다른 멤버에게 OWNER를 위임한 뒤 퇴실해야 합니다.",
                        OpenChatErrorCode.OWNER_TRANSFER_REQUIRED
                );
            }
            throw new BusinessException(
                    "유일한 OWNER는 방 나가기 대신 OPEN 채팅방을 종료해야 합니다.",
                    OpenChatErrorCode.OWNER_CLOSE_REQUIRED
            );
        }

        OpenChatMemberProfile profile = getProfile(member.getId());
        member.leaveOpenChat();

        memberRepository.flush();

        return new OpenChatMembershipResponseDto(
                openChatRoom.getChatRoom().getId(),
                false,
                member.getRole(),
                profileResponseMapper.toResponse(profile)
        );
    }

    public OpenChatRoomDetailResponseDto transferOwner(
            Long loginUserId,
            Long roomId,
            OpenChatOwnerTransferRequestDto request
    ) {
        OpenChatRoom openChatRoom = getLockedRoom(roomId);
        validateJoinable(openChatRoom);

        ChatRoomMember currentOwner = getActiveMember(
                loginUserId,
                roomId
        );
        validateOwner(currentOwner);

        if (request == null
                || request.targetOpenChatMemberId() == null
                || request.targetOpenChatMemberId() <= 0) {
            throw new BusinessException(
                    "OWNER 위임 대상 멤버는 필수입니다.",
                    OpenChatErrorCode.OWNER_TRANSFER_TARGET_REQUIRED
            );
        }

        ChatRoomMember targetMember = memberRepository
                .findByIdAndChatRoomIdAndActiveTrueAndDeletedAtIsNull(
                        request.targetOpenChatMemberId(),
                        roomId
                )
                .orElseThrow(() -> new BusinessException(
                        "OWNER 위임 대상 멤버를 찾을 수 없습니다.",
                        OpenChatErrorCode.OWNER_TRANSFER_TARGET_INVALID
                ));

        if (currentOwner.getId().equals(targetMember.getId())) {
            throw new BusinessException(
                    "자기 자신에게 OWNER를 위임할 수 없습니다.",
                    OpenChatErrorCode.OWNER_TRANSFER_TARGET_INVALID
            );
        }

        currentOwner.changeRole(ChatRoomMemberRole.MEMBER);
        targetMember.changeRole(ChatRoomMemberRole.OWNER);
        openChatRoom.getChatRoom().transferOwnership(
                targetMember.getUser()
        );

        memberRepository.flush();
        chatRoomRepository.flush();

        return roomQueryService.getDetail(loginUserId, roomId);
    }

    public OpenChatRoomDetailResponseDto close(
            Long loginUserId,
            Long roomId
    ) {
        OpenChatRoom openChatRoom = getLockedRoom(roomId);
        ChatRoomMember owner = getActiveMember(
                loginUserId,
                roomId
        );
        validateOwner(owner);

        if (!openChatRoom.isClosed()) {
            openChatRoom.close();
            eventPublisher.publishEvent(
                    OpenChatRoomClosedApplicationEvent.of(
                            roomId,
                            openChatRoom.getClosedAt()
                    )
            );
        }

        openChatRoomRepository.flush();
        return roomQueryService.getDetail(loginUserId, roomId);
    }

    private void createMember(
            Long loginUserId,
            ChatRoom chatRoom,
            OpenChatJoinRequestDto request,
            ChatLanguageSettingResult languageSetting,
            Long latestMessageId
    ) {
        OpenChatProfileRequestDto profileRequest = requireJoinProfile(
                request
        );
        User user = userService.getById(loginUserId);

        ChatRoomMember member = ChatRoomMember.createMember(
                chatRoom,
                user,
                languageSetting.originalLanguageCode(),
                languageSetting.translationLanguageCode(),
                languageSetting.showOriginal(),
                languageSetting.showTranslation()
        );
        member.initializeReadCursor(latestMessageId);
        ChatRoomMember savedMember = memberRepository.save(member);

        String nickname = profileValidator.normalizeNickname(
                profileRequest.nickname()
        );
        String objectKey = profileValidator.normalizeObjectKey(
                profileRequest.profileImageObjectKey(),
                savedMember.getId()
        );

        profileRepository.save(OpenChatMemberProfile.create(
                savedMember,
                memberCodeGenerator.generate(),
                nickname,
                objectKey
        ));
    }

    private void restoreMember(
            ChatRoomMember member,
            OpenChatJoinRequestDto request,
            ChatLanguageSettingResult languageSetting,
            Long latestMessageId
    ) {
        OpenChatMemberProfile profile = getProfile(member.getId());

        member.restore(
                ChatRoomMemberRole.MEMBER,
                languageSetting.originalLanguageCode(),
                languageSetting.translationLanguageCode(),
                languageSetting.showOriginal(),
                languageSetting.showTranslation()
        );
        member.initializeReadCursor(latestMessageId);

        if (request != null && request.profile() != null) {
            OpenChatProfileRequestDto profileRequest = request.profile();
            String nickname = profileValidator.normalizeNickname(
                    profileRequest.nickname()
            );
            String requestedObjectKey = profileValidator.normalizeObjectKey(
                    profileRequest.profileImageObjectKey(),
                    member.getId()
            );
            if (requestedObjectKey != null
                    && !Objects.equals(
                            requestedObjectKey,
                            profile.getProfileImageObjectKey()
                    )) {
                throw new BusinessException(
                        "재참여 시 프로필 이미지는 참여 완료 후 이미지 API로 변경해야 합니다.",
                        OpenChatErrorCode.PROFILE_IMAGE_OBJECT_KEY_INVALID
                );
            }
            profile.updateNickname(nickname);
            publishProfileUpdated(profile);
        }
    }

    private OpenChatProfileRequestDto requireJoinProfile(
            OpenChatJoinRequestDto request
    ) {
        if (request == null || request.profile() == null) {
            throw new BusinessException(
                    "최초 참여 시 OPEN 프로필은 필수입니다.",
                    OpenChatErrorCode.JOIN_PROFILE_REQUIRED
            );
        }
        return request.profile();
    }

    private OpenChatRoom getLockedRoom(Long roomId) {
        if (roomId == null || roomId <= 0) {
            throw roomNotFound();
        }
        return openChatRoomRepository
                .findByChatRoomIdForUpdate(roomId)
                .orElseThrow(this::roomNotFound);
    }

    private ChatRoomMember getActiveMember(
            Long userId,
            Long roomId
    ) {
        return memberRepository
                .findByChatRoomIdAndUserIdAndActiveTrueAndDeletedAtIsNull(
                        roomId,
                        userId
                )
                .orElseThrow(() -> new BusinessException(
                        "OPEN 채팅방 멤버가 아니거나 접근 권한이 없습니다.",
                        OpenChatErrorCode.MEMBER_ACCESS_DENIED
                ));
    }

    private OpenChatMemberProfile getProfile(Long memberId) {
        return profileRepository.findByChatRoomMemberId(memberId)
                .orElseThrow(() -> new BusinessException(
                        "OPEN 채팅 프로필을 찾을 수 없습니다.",
                        OpenChatErrorCode.PROFILE_NOT_FOUND
                ));
    }

    private void validateJoinable(OpenChatRoom openChatRoom) {
        if (openChatRoom.isClosed()) {
            throw new BusinessException(
                    "종료된 OPEN 채팅방에는 참여할 수 없습니다.",
                    OpenChatErrorCode.ROOM_CLOSED
            );
        }
    }

    private void validateOwner(ChatRoomMember member) {
        if (!member.isOwner()) {
            throw new BusinessException(
                    "OPEN 채팅방 OWNER만 수행할 수 있습니다.",
                    OpenChatErrorCode.OWNER_ONLY
            );
        }
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

    private BusinessException roomNotFound() {
        return new BusinessException(
                "OPEN 채팅방을 찾을 수 없습니다.",
                OpenChatErrorCode.ROOM_NOT_FOUND
        );
    }
}
