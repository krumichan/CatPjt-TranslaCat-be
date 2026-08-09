package jp.co.translacat.domain.chat.member.service;

import jp.co.translacat.domain.chat.language.dto.ChatLanguageSettingResult;
import jp.co.translacat.domain.chat.language.enums.ChatLanguageSettingSource;
import jp.co.translacat.domain.chat.language.service.UserChatLanguageSettingService;
import jp.co.translacat.domain.chat.member.dto.request.ChatRoomMemberInvitationRequestDto;
import jp.co.translacat.domain.chat.member.dto.response.ChatRoomInvitationResponseDto;
import jp.co.translacat.domain.chat.member.dto.response.ChatRoomInvitedMemberResponseDto;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.event.ChatRoomMembersChangedApplicationEvent;
import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.message.dto.response.ChatMessageResponseDto;
import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.enums.ChatMessageStatus;
import jp.co.translacat.domain.chat.message.repository.ChatMessageRepository;
import jp.co.translacat.domain.chat.room.dto.request.ChatRoomGroupConversionRequestDto;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomSourceType;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import jp.co.translacat.domain.chat.room.repository.ChatRoomRepository;
import jp.co.translacat.domain.chat.websocket.service.ChatWebSocketEventPublisher;
import jp.co.translacat.domain.user.block.service.UserBlockService;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.friend.service.FriendService;
import jp.co.translacat.domain.user.profile.dto.UserSummaryProfileResponseDto;
import jp.co.translacat.domain.user.profile.service.UserProfileQueryService;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.global.utils.ValidationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatRoomInvitationService {

    private static final int MAX_GROUP_NAME_LENGTH = 100;
    private static final int MAX_GROUP_DESCRIPTION_LENGTH = 500;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final UserBlockService userBlockService;
    private final FriendService friendService;
    private final UserProfileQueryService userProfileQueryService;
    private final UserChatLanguageSettingService userChatLanguageSettingService;
    private final ChatWebSocketEventPublisher chatWebSocketEventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;

    public ChatRoomInvitationResponseDto inviteMembers(
            Long loginUserId,
            Long chatRoomId,
            ChatRoomMemberInvitationRequestDto request
    ) {
        ChatRoom chatRoom = getLockedActiveRoom(chatRoomId);
        validateExistingGroupRoom(chatRoom);

        ChatRoomMember requesterMember = getActiveMember(
                loginUserId,
                chatRoomId
        );
        validateGroupInvitePermission(requesterMember);

        List<User> targetUsers = resolveTargetUsers(
                loginUserId,
                request != null ? request.targetUserIds() : null,
                request != null ? request.targetPublicIds() : null,
                "CHAT_ROOM_INVITE_TARGET_REQUIRED"
        );
        validateGroupInviteTargets(chatRoomId, targetUsers);

        Long initialReadMessageId = findLatestSentMessageId(chatRoomId);
        List<ChatRoomMember> invitedMembers = targetUsers.stream()
                .map(user -> addOrRestoreMember(
                        chatRoom,
                        user,
                        initialReadMessageId
                ))
                .toList();

        ChatMessageResponseDto systemMessage =
                createAndPublishInvitationSystemMessage(
                        chatRoom,
                        requesterMember.getUser(),
                        invitedMembers
                );

        /*
         * publish를 호출하지 않은 것으로 판단되는 정적 분석을 피하면서도,
         * 반환 DTO에는 SYSTEM 메시지를 중복 포함하지 않는다.
         */
        if (systemMessage == null) {
            throw new IllegalStateException(
                    "초대 SYSTEM 메시지를 생성하지 못했습니다."
            );
        }

        applicationEventPublisher.publishEvent(
                ChatRoomMembersChangedApplicationEvent.of(chatRoomId)
        );

        return ChatRoomInvitationResponseDto.forExistingGroup(
                chatRoom.getId(),
                toInvitedMemberResponses(invitedMembers)
        );
    }

    public ChatRoomInvitationResponseDto convertDirectToGroup(
            Long loginUserId,
            Long chatRoomId,
            ChatRoomGroupConversionRequestDto request
    ) {
        ChatRoom directRoom = getLockedActiveRoom(chatRoomId);
        validateFriendDirectRoom(directRoom);
        validateGroupConversionRequest(request);

        ChatRoomMember requesterMember = getActiveMember(
                loginUserId,
                chatRoomId
        );
        List<ChatRoomMember> directMembers = chatRoomMemberRepository
                .findByChatRoomIdAndActiveTrueAndDeletedAtIsNull(
                        chatRoomId
                );
        validateDirectRoomMembers(loginUserId, directMembers);

        List<User> requestedTargets = resolveTargetUsers(
                loginUserId,
                request.targetUserIds(),
                request.targetPublicIds(),
                "CHAT_ROOM_DIRECT_CONVERSION_TARGET_REQUIRED"
        );

        Set<Long> directMemberUserIds = directMembers.stream()
                .map(ChatRoomMember::getUser)
                .map(User::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<User> newTargetUsers = requestedTargets.stream()
                .filter(user -> !directMemberUserIds.contains(user.getId()))
                .toList();

        if (newTargetUsers.isEmpty()) {
            throw new BusinessException(
                    "새 그룹 채팅방에 추가할 사용자가 필요합니다.",
                    "CHAT_ROOM_DIRECT_CONVERSION_TARGET_REQUIRED"
            );
        }

        ChatRoomSourceType groupSourceType =
                resolveConvertedGroupSourceType(
                        loginUserId,
                        newTargetUsers
                );

        ChatRoom groupRoom = ChatRoom.createGroupRoom(
                request.name().trim(),
                normalizeNullableText(request.description()),
                requesterMember.getUser(),
                groupSourceType
        );
        ChatRoom savedGroupRoom = chatRoomRepository.save(groupRoom);

        ChatRoomMember ownerMember = createMember(
                savedGroupRoom,
                requesterMember.getUser(),
                ChatRoomMemberRole.OWNER
        );
        chatRoomMemberRepository.save(ownerMember);

        LinkedHashMap<Long, User> groupMemberUsers =
                new LinkedHashMap<>();
        for (ChatRoomMember directMember : directMembers) {
            User user = directMember.getUser();
            if (!user.getId().equals(loginUserId)) {
                groupMemberUsers.put(user.getId(), user);
            }
        }
        for (User targetUser : newTargetUsers) {
            groupMemberUsers.put(targetUser.getId(), targetUser);
        }

        List<ChatRoomMember> invitedMembers =
                groupMemberUsers.values()
                        .stream()
                        .map(user -> createMember(
                                savedGroupRoom,
                                user,
                                ChatRoomMemberRole.MEMBER
                        ))
                        .map(chatRoomMemberRepository::save)
                        .toList();

        return ChatRoomInvitationResponseDto.forNewGroup(
                savedGroupRoom.getId(),
                toInvitedMemberResponses(invitedMembers)
        );
    }

    private ChatRoom getLockedActiveRoom(Long chatRoomId) {
        if (chatRoomId == null) {
            throw new BusinessException(
                    "채팅방 ID는 필수입니다.",
                    "CHAT_ROOM_ID_REQUIRED"
            );
        }
        return chatRoomRepository
                .findActiveByIdForUpdate(chatRoomId)
                .orElseThrow(() -> new BusinessException(
                        "채팅방을 찾을 수 없습니다.",
                        "CHAT_ROOM_NOT_FOUND"
                ));
    }

    private ChatRoomMember getActiveMember(
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

    private void validateExistingGroupRoom(ChatRoom chatRoom) {
        if (chatRoom.getRoomType() != ChatRoomType.GROUP) {
            throw new BusinessException(
                    "기존 멤버 초대는 그룹 채팅방에서만 가능합니다.",
                    "CHAT_ROOM_INVITE_UNSUPPORTED_ROOM_TYPE"
            );
        }
        if (chatRoom.getSourceType() == ChatRoomSourceType.OPEN
                || chatRoom.getSourceType() == ChatRoomSourceType.AI) {
            throw new BusinessException(
                    "해당 채팅방 타입에서는 멤버를 초대할 수 없습니다.",
                    "CHAT_ROOM_INVITE_UNSUPPORTED_ROOM_TYPE"
            );
        }
    }

    private void validateFriendDirectRoom(ChatRoom chatRoom) {
        boolean supported =
                chatRoom.getRoomType() == ChatRoomType.DIRECT
                        && chatRoom.getSourceType()
                        == ChatRoomSourceType.FRIEND;
        if (!supported) {
            throw new BusinessException(
                    "FRIEND DIRECT 채팅방에서만 새 그룹으로 전환할 수 있습니다.",
                    "CHAT_ROOM_INVITE_UNSUPPORTED_ROOM_TYPE"
            );
        }
    }

    private void validateGroupInvitePermission(
            ChatRoomMember requesterMember
    ) {
        if (!requesterMember.isOwner() && !requesterMember.isAdmin()) {
            throw new BusinessException(
                    "OWNER 또는 ADMIN만 멤버를 초대할 수 있습니다.",
                    "CHAT_ROOM_INVITE_NOT_ALLOWED"
            );
        }
    }

    private void validateGroupConversionRequest(
            ChatRoomGroupConversionRequestDto request
    ) {
        if (request == null) {
            throw new BusinessException(
                    "그룹 전환 요청은 필수입니다.",
                    "CHAT_ROOM_DIRECT_CONVERSION_TARGET_REQUIRED"
            );
        }
        if (ValidationUtil.isBlank(request.name())) {
            throw new BusinessException(
                    "그룹 이름은 필수입니다.",
                    "CHAT_ROOM_GROUP_NAME_REQUIRED"
            );
        }

        String normalizedName = request.name().trim();
        if (normalizedName.length() > MAX_GROUP_NAME_LENGTH) {
            throw new BusinessException(
                    "그룹 이름은 100자 이하로 입력해주세요.",
                    "CHAT_ROOM_GROUP_NAME_TOO_LONG"
            );
        }

        String normalizedDescription = normalizeNullableText(
                request.description()
        );
        if (normalizedDescription != null
                && normalizedDescription.length()
                > MAX_GROUP_DESCRIPTION_LENGTH) {
            throw new BusinessException(
                    "그룹 설명은 500자 이하로 입력해주세요.",
                    "CHAT_ROOM_GROUP_DESCRIPTION_TOO_LONG"
            );
        }
    }

    private void validateDirectRoomMembers(
            Long loginUserId,
            List<ChatRoomMember> directMembers
    ) {
        boolean requesterIncluded = directMembers.stream()
                .map(ChatRoomMember::getUser)
                .map(User::getId)
                .anyMatch(loginUserId::equals);

        if (!requesterIncluded || directMembers.size() != 2) {
            throw new BusinessException(
                    "유효한 1:1 채팅방 멤버 구성이 아닙니다.",
                    "CHAT_ROOM_DIRECT_MEMBER_INVALID"
            );
        }
    }

    private List<User> resolveTargetUsers(
            Long loginUserId,
            Collection<Long> targetUserIds,
            Collection<String> targetPublicIds,
            String emptyTargetErrorCode
    ) {
        LinkedHashMap<Long, User> resolvedUsers = new LinkedHashMap<>();

        if (targetUserIds != null) {
            for (Long targetUserId : targetUserIds) {
                User user = getTargetByUserId(targetUserId);
                resolvedUsers.put(user.getId(), user);
            }
        }
        if (targetPublicIds != null) {
            for (String targetPublicId : targetPublicIds) {
                User user = getTargetByPublicId(targetPublicId);
                resolvedUsers.put(user.getId(), user);
            }
        }

        if (resolvedUsers.isEmpty()) {
            throw new BusinessException(
                    "초대 대상 사용자는 최소 1명 이상 필요합니다.",
                    emptyTargetErrorCode
            );
        }

        for (User targetUser : resolvedUsers.values()) {
            validateTargetUser(loginUserId, targetUser);
        }

        return new ArrayList<>(resolvedUsers.values());
    }

    private User getTargetByUserId(Long targetUserId) {
        if (targetUserId == null) {
            throw new BusinessException(
                    "초대 대상 사용자를 찾을 수 없습니다.",
                    "CHAT_ROOM_INVITE_TARGET_NOT_FOUND"
            );
        }
        return userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(
                        "초대 대상 사용자를 찾을 수 없습니다.",
                        "CHAT_ROOM_INVITE_TARGET_NOT_FOUND"
                ));
    }

    private User getTargetByPublicId(String targetPublicId) {
        if (ValidationUtil.isBlank(targetPublicId)) {
            throw new BusinessException(
                    "초대 대상 사용자를 찾을 수 없습니다.",
                    "CHAT_ROOM_INVITE_TARGET_NOT_FOUND"
            );
        }
        return userRepository
                .findByPublicId(targetPublicId.trim())
                .orElseThrow(() -> new BusinessException(
                        "초대 대상 사용자를 찾을 수 없습니다.",
                        "CHAT_ROOM_INVITE_TARGET_NOT_FOUND"
                ));
    }

    private void validateTargetUser(
            Long loginUserId,
            User targetUser
    ) {
        if (targetUser.getId().equals(loginUserId)) {
            throw new BusinessException(
                    "자기 자신을 초대할 수 없습니다.",
                    "CHAT_ROOM_INVITE_SELF_NOT_ALLOWED"
            );
        }
        if (userBlockService.isBlockedBetween(
                loginUserId,
                targetUser.getId()
        )) {
            throw new BusinessException(
                    "차단 관계인 사용자는 초대할 수 없습니다.",
                    "CHAT_ROOM_INVITE_TARGET_BLOCKED"
            );
        }
    }

    private void validateGroupInviteTargets(
            Long chatRoomId,
            List<User> targetUsers
    ) {
        for (User targetUser : targetUsers) {
            boolean alreadyActive = chatRoomMemberRepository
                    .existsByChatRoomIdAndUserIdAndActiveTrueAndDeletedAtIsNull(
                            chatRoomId,
                            targetUser.getId()
                    );
            if (alreadyActive) {
                throw new BusinessException(
                        "이미 채팅방에 참여 중인 사용자입니다.",
                        "CHAT_ROOM_INVITE_ALREADY_MEMBER"
                );
            }
        }
    }

    private ChatRoomMember addOrRestoreMember(
            ChatRoom chatRoom,
            User targetUser,
            Long initialReadMessageId
    ) {
        ChatLanguageSettingResult languageSetting =
                resolveMemberLanguageSetting(targetUser.getId());

        return chatRoomMemberRepository
                .findByChatRoomIdAndUserId(
                        chatRoom.getId(),
                        targetUser.getId()
                )
                .map(member -> {
                    member.restore(
                            ChatRoomMemberRole.MEMBER,
                            languageSetting.originalLanguageCode(),
                            languageSetting.translationLanguageCode(),
                            languageSetting.showOriginal(),
                            languageSetting.showTranslation()
                    );
                    member.initializeReadCursor(initialReadMessageId);
                    return member;
                })
                .orElseGet(() -> {
                    ChatRoomMember member = ChatRoomMember.createMember(
                            chatRoom,
                            targetUser,
                            languageSetting.originalLanguageCode(),
                            languageSetting.translationLanguageCode(),
                            languageSetting.showOriginal(),
                            languageSetting.showTranslation()
                    );
                    member.initializeReadCursor(initialReadMessageId);
                    return chatRoomMemberRepository.save(member);
                });
    }

    private ChatRoomMember createMember(
            ChatRoom chatRoom,
            User user,
            ChatRoomMemberRole role
    ) {
        ChatLanguageSettingResult languageSetting =
                resolveMemberLanguageSetting(user.getId());

        if (role == ChatRoomMemberRole.OWNER) {
            return ChatRoomMember.createOwner(
                    chatRoom,
                    user,
                    languageSetting.originalLanguageCode(),
                    languageSetting.translationLanguageCode(),
                    languageSetting.showOriginal(),
                    languageSetting.showTranslation()
            );
        }
        return ChatRoomMember.createMember(
                chatRoom,
                user,
                languageSetting.originalLanguageCode(),
                languageSetting.translationLanguageCode(),
                languageSetting.showOriginal(),
                languageSetting.showTranslation()
        );
    }

    private Long findLatestSentMessageId(Long chatRoomId) {
        return chatMessageRepository
                .findTopByChatRoomIdAndStatusAndDeletedAtIsNullOrderByIdDesc(
                        chatRoomId,
                        ChatMessageStatus.SENT
                )
                .map(ChatMessage::getId)
                .orElse(null);
    }

    private ChatLanguageSettingResult resolveMemberLanguageSetting(
            Long userId
    ) {
        if (userChatLanguageSettingService != null) {
            ChatLanguageSettingResult resolved =
                    userChatLanguageSettingService.resolveDefault(userId);
            if (resolved != null) {
                return resolved;
            }
        }
        return new ChatLanguageSettingResult(
                "ko",
                "ja",
                true,
                true,
                false,
                ChatLanguageSettingSource.SYSTEM
        );
    }

    private ChatRoomSourceType resolveConvertedGroupSourceType(
            Long loginUserId,
            List<User> targetUsers
    ) {
        boolean allTargetsAreFriends = targetUsers.stream()
                .allMatch(targetUser -> friendService.areFriends(
                        loginUserId,
                        targetUser.getId()
                ));

        return allTargetsAreFriends
                ? ChatRoomSourceType.FRIEND
                : ChatRoomSourceType.MANUAL;
    }

    private ChatMessageResponseDto createAndPublishInvitationSystemMessage(
            ChatRoom chatRoom,
            User requester,
            List<ChatRoomMember> invitedMembers
    ) {
        String invitedNames = invitedMembers.stream()
                .map(ChatRoomMember::getUser)
                .map(this::resolveDisplayName)
                .collect(Collectors.joining(", "));

        String content = requester.getUsername()
                + "님이 "
                + invitedNames
                + "님을 초대했습니다.";

        ChatMessage systemMessage = ChatMessage.createSystemMessage(
                chatRoom,
                content
        );
        ChatMessage savedMessage = chatMessageRepository.save(systemMessage);
        ChatMessageResponseDto response = ChatMessageResponseDto.from(
                savedMessage,
                List.of()
        );

        chatWebSocketEventPublisher.publishMessageCreated(
                chatRoom.getId(),
                response
        );
        return response;
    }

    private String resolveDisplayName(User user) {
        UserSummaryProfileResponseDto profile =
                userProfileQueryService.getSummaryByUser(user);

        if (!ValidationUtil.isBlank(profile.nickname())) {
            return profile.nickname();
        }
        if (!ValidationUtil.isBlank(user.getUsername())) {
            return user.getUsername();
        }
        return profile.publicId();
    }

    private List<ChatRoomInvitedMemberResponseDto>
    toInvitedMemberResponses(List<ChatRoomMember> invitedMembers) {
        return invitedMembers.stream()
                .map(member -> ChatRoomInvitedMemberResponseDto.of(
                        member,
                        userProfileQueryService.getSummaryByUser(
                                member.getUser()
                        )
                ))
                .toList();
    }

    private String normalizeNullableText(String value) {
        if (ValidationUtil.isBlank(value)) {
            return null;
        }
        return value.trim();
    }
}
