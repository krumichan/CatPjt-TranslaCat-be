package jp.co.translacat.domain.chat.notification.service;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.repository.ChatMessageRepository;
import jp.co.translacat.domain.chat.notification.dto.response.ChatNotificationActivityItemResponseDto;
import jp.co.translacat.domain.chat.notification.dto.response.ChatNotificationActivityListResponseDto;
import jp.co.translacat.domain.chat.notification.dto.response.ChatNotificationChatItemResponseDto;
import jp.co.translacat.domain.chat.notification.dto.response.ChatNotificationChatListResponseDto;
import jp.co.translacat.domain.chat.notification.dto.response.ChatNotificationLatestMessageResponseDto;
import jp.co.translacat.domain.chat.notification.dto.response.ChatNotificationSummaryResponseDto;
import jp.co.translacat.domain.chat.notification.entity.ChatNotification;
import jp.co.translacat.domain.chat.notification.repository.ChatNotificationChatQueryRepository;
import jp.co.translacat.domain.chat.notification.repository.ChatNotificationRepository;
import jp.co.translacat.domain.chat.notification.support.ChatNotificationErrorCode;
import jp.co.translacat.domain.chat.notification.repository.projection.ChatNotificationRoomQueryRow;
import jp.co.translacat.domain.chat.notification.repository.projection.ChatNotificationUnreadSummary;
import jp.co.translacat.domain.chat.openchat.profile.entity.OpenChatMemberProfile;
import jp.co.translacat.domain.chat.openchat.profile.repository.OpenChatMemberProfileRepository;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.profile.entity.UserProfile;
import jp.co.translacat.domain.user.profile.repository.UserProfileRepository;
import jp.co.translacat.domain.user.profile.storage.service.UserProfileImageUrlResolver;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatNotificationQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MESSAGE_PREVIEW_MAX_LENGTH = 160;

    private final ChatNotificationChatQueryRepository chatQueryRepository;
    private final ChatNotificationRepository notificationRepository;
    private final ChatNotificationActivityResponseMapper activityResponseMapper;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserProfileImageUrlResolver userProfileImageUrlResolver;
    private final OpenChatMemberProfileRepository openChatMemberProfileRepository;

    public ChatNotificationSummaryResponseDto getSummary(Long loginUserId) {
        ChatNotificationUnreadSummary unreadSummary =
                chatQueryRepository.summarizeUnreadChats(
                        loginUserId
                );

        long unreadActivityCount =
                notificationRepository.countUnreadActivities(loginUserId);
        return ChatNotificationSummaryResponseDto.of(
                unreadSummary.unreadMessageCount(),
                unreadSummary.unreadRoomCount(),
                unreadActivityCount
        );
    }

    public ChatNotificationActivityListResponseDto getActivities(
            Long loginUserId,
            Boolean onlyUnread,
            Long cursorId,
            Integer requestedSize
    ) {
        validateActivityCursor(cursorId);
        int size = normalizePageSize(requestedSize);

        List<ChatNotification> fetched =
                notificationRepository.findActivityPage(
                        loginUserId,
                        Boolean.TRUE.equals(onlyUnread),
                        cursorId,
                        size + 1
                );
        boolean hasNext = fetched.size() > size;
        List<ChatNotification> page = hasNext
                ? new ArrayList<>(fetched.subList(0, size))
                : new ArrayList<>(fetched);

        List<ChatNotificationActivityItemResponseDto> items = page
                .stream()
                .map(activityResponseMapper::toResponse)
                .toList();

        Long nextCursorId = hasNext && !page.isEmpty()
                ? page.get(page.size() - 1).getId()
                : null;

        return ChatNotificationActivityListResponseDto.of(
                items,
                nextCursorId,
                hasNext
        );
    }

    public ChatNotificationChatListResponseDto getUnreadChats(
            Long loginUserId,
            Long cursorMessageId,
            Integer requestedSize
    ) {
        validateCursor(cursorMessageId);
        int size = normalizePageSize(requestedSize);

        List<ChatNotificationRoomQueryRow> fetched =
                chatQueryRepository.findUnreadChatRoomPage(
                        loginUserId,
                        cursorMessageId,
                        size + 1
                );
        boolean hasNext = fetched.size() > size;
        List<ChatNotificationRoomQueryRow> pageRows = hasNext
                ? new ArrayList<>(fetched.subList(0, size))
                : new ArrayList<>(fetched);

        if (pageRows.isEmpty()) {
            return ChatNotificationChatListResponseDto.of(
                    List.of(),
                    null,
                    false
            );
        }

        Map<Long, ChatMessage> latestMessagesById =
                findLatestMessagesById(pageRows);
        List<Long> roomIds = pageRows.stream()
                .map(ChatNotificationRoomQueryRow::roomId)
                .toList();
        Map<Long, List<ChatRoomMember>> membersByRoomId =
                findMembersByRoomId(roomIds);
        Map<Long, UserProfile> profilesByUserId =
                findUserProfiles(
                        membersByRoomId,
                        latestMessagesById.values()
                );
        Map<OpenSenderKey, OpenChatMemberProfile> openProfiles =
                findOpenSenderProfiles(
                        pageRows,
                        latestMessagesById.values()
                );

        List<ChatNotificationChatItemResponseDto> items = pageRows
                .stream()
                .map(row -> toChatItem(
                        loginUserId,
                        row,
                        latestMessagesById.get(row.latestMessageId()),
                        membersByRoomId.getOrDefault(
                                row.roomId(),
                                List.of()
                        ),
                        profilesByUserId,
                        openProfiles
                ))
                .filter(Objects::nonNull)
                .toList();

        Long nextCursorMessageId = hasNext && !pageRows.isEmpty()
                ? pageRows.get(pageRows.size() - 1).latestMessageId()
                : null;

        return ChatNotificationChatListResponseDto.of(
                items,
                nextCursorMessageId,
                hasNext
        );
    }

    private Map<Long, ChatMessage> findLatestMessagesById(
            List<ChatNotificationRoomQueryRow> rows
    ) {
        List<Long> messageIds = rows.stream()
                .map(ChatNotificationRoomQueryRow::latestMessageId)
                .distinct()
                .toList();
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        return chatMessageRepository
                .findByIdInAndDeletedAtIsNull(messageIds)
                .stream()
                .collect(Collectors.toMap(
                        ChatMessage::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Map<Long, List<ChatRoomMember>> findMembersByRoomId(
            List<Long> roomIds
    ) {
        if (roomIds.isEmpty()) {
            return Map.of();
        }
        return chatRoomMemberRepository
                .findByChatRoomIdInAndActiveTrueAndDeletedAtIsNull(
                        roomIds
                )
                .stream()
                .collect(Collectors.groupingBy(
                        member -> member.getChatRoom().getId()
                ));
    }

    private Map<Long, UserProfile> findUserProfiles(
            Map<Long, List<ChatRoomMember>> membersByRoomId,
            Collection<ChatMessage> latestMessages
    ) {
        Set<Long> userIds = membersByRoomId.values()
                .stream()
                .flatMap(List::stream)
                .map(ChatRoomMember::getUser)
                .filter(Objects::nonNull)
                .map(User::getId)
                .collect(Collectors.toSet());

        latestMessages.stream()
                .map(ChatMessage::getSenderUser)
                .filter(Objects::nonNull)
                .map(User::getId)
                .forEach(userIds::add);

        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userProfileRepository
                .findByUserIdInAndDeletedFalse(userIds)
                .stream()
                .collect(Collectors.toMap(
                        profile -> profile.getUser().getId(),
                        Function.identity(),
                        (left, right) -> left
                ));
    }

    private Map<OpenSenderKey, OpenChatMemberProfile>
    findOpenSenderProfiles(
            List<ChatNotificationRoomQueryRow> rows,
            Collection<ChatMessage> latestMessages
    ) {
        Set<Long> openRoomIds = rows.stream()
                .filter(row -> row.roomType() == ChatRoomType.OPEN)
                .map(ChatNotificationRoomQueryRow::roomId)
                .collect(Collectors.toSet());
        Set<Long> senderUserIds = latestMessages.stream()
                .filter(message -> message.getChatRoom() != null)
                .filter(message -> message.getChatRoom().getRoomType()
                        == ChatRoomType.OPEN)
                .map(ChatMessage::getSenderUser)
                .filter(Objects::nonNull)
                .map(User::getId)
                .collect(Collectors.toSet());

        if (openRoomIds.isEmpty() || senderUserIds.isEmpty()) {
            return Map.of();
        }

        Map<OpenSenderKey, OpenChatMemberProfile> result =
                new LinkedHashMap<>();
        for (OpenChatMemberProfile profile :
                openChatMemberProfileRepository
                        .findByChatRoomMemberChatRoomIdInAndChatRoomMemberUserIdIn(
                                openRoomIds,
                                senderUserIds
                        )) {
            ChatRoomMember member = profile.getChatRoomMember();
            if (member == null
                    || member.getChatRoom() == null
                    || member.getUser() == null) {
                continue;
            }
            result.putIfAbsent(
                    new OpenSenderKey(
                            member.getChatRoom().getId(),
                            member.getUser().getId()
                    ),
                    profile
            );
        }
        return Map.copyOf(result);
    }

    private ChatNotificationChatItemResponseDto toChatItem(
            Long loginUserId,
            ChatNotificationRoomQueryRow row,
            ChatMessage latestMessage,
            List<ChatRoomMember> members,
            Map<Long, UserProfile> profilesByUserId,
            Map<OpenSenderKey, OpenChatMemberProfile> openProfiles
    ) {
        if (latestMessage == null) {
            return null;
        }

        RoomDisplay roomDisplay = resolveRoomDisplay(
                loginUserId,
                row,
                members,
                profilesByUserId
        );
        String senderDisplayName = resolveSenderDisplayName(
                row,
                latestMessage,
                profilesByUserId,
                openProfiles
        );

        ChatNotificationLatestMessageResponseDto latest =
                new ChatNotificationLatestMessageResponseDto(
                        latestMessage.getId(),
                        senderDisplayName,
                        latestMessage.getMessageType(),
                        toPreview(latestMessage.getContent()),
                        latestMessage.getCreatedAt()
                );

        return new ChatNotificationChatItemResponseDto(
                row.roomId(),
                row.roomType(),
                row.sourceType(),
                roomDisplay.name(),
                roomDisplay.avatarUrl(),
                latest,
                row.unreadCount(),
                row.firstUnreadMessageId()
        );
    }

    private RoomDisplay resolveRoomDisplay(
            Long loginUserId,
            ChatNotificationRoomQueryRow row,
            List<ChatRoomMember> members,
            Map<Long, UserProfile> profilesByUserId
    ) {
        if (row.roomType() != ChatRoomType.DIRECT) {
            return new RoomDisplay(row.roomName(), null);
        }

        User partner = members.stream()
                .map(ChatRoomMember::getUser)
                .filter(Objects::nonNull)
                .filter(user -> !Objects.equals(
                        user.getId(),
                        loginUserId
                ))
                .findFirst()
                .orElse(null);
        if (partner == null) {
            return new RoomDisplay(row.roomName(), null);
        }

        UserProfile profile = profilesByUserId.get(partner.getId());
        String name = profile != null
                ? profile.getNickname()
                : fallbackUserName(partner);
        String avatarUrl = profile != null
                ? userProfileImageUrlResolver
                        .resolveProfileImageUrl(profile)
                : null;
        return new RoomDisplay(name, avatarUrl);
    }

    private String resolveSenderDisplayName(
            ChatNotificationRoomQueryRow row,
            ChatMessage message,
            Map<Long, UserProfile> profilesByUserId,
            Map<OpenSenderKey, OpenChatMemberProfile> openProfiles
    ) {
        if (message.getSenderAiMember() != null
                && message.getSenderAiMember().getAiAgent() != null) {
            return message.getSenderAiMember()
                    .getAiAgent()
                    .getNickname();
        }

        User sender = message.getSenderUser();
        if (sender == null) {
            return null;
        }

        if (row.roomType() == ChatRoomType.OPEN) {
            OpenChatMemberProfile openProfile = openProfiles.get(
                    new OpenSenderKey(row.roomId(), sender.getId())
            );
            return openProfile != null
                    ? openProfile.getNickname()
                    : null;
        }

        UserProfile profile = profilesByUserId.get(sender.getId());
        return profile != null
                ? profile.getNickname()
                : fallbackUserName(sender);
    }

    private String fallbackUserName(User user) {
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername();
        }
        return user.getPublicId();
    }

    private String toPreview(String content) {
        if (content == null) {
            return null;
        }
        String normalized = content
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim();
        if (normalized.length() <= MESSAGE_PREVIEW_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MESSAGE_PREVIEW_MAX_LENGTH);
    }

    private int normalizePageSize(Integer requestedSize) {
        if (requestedSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (requestedSize <= 0) {
            throw new BusinessException(
                    "size는 1 이상이어야 합니다."
            );
        }
        return Math.min(requestedSize, MAX_PAGE_SIZE);
    }

    private void validateActivityCursor(Long cursorId) {
        if (cursorId != null && cursorId <= 0L) {
            throw new BusinessException(
                    "cursorId는 1 이상이어야 합니다.",
                    ChatNotificationErrorCode.CURSOR_INVALID
            );
        }
    }

    private void validateCursor(Long cursorMessageId) {
        if (cursorMessageId != null && cursorMessageId <= 0L) {
            throw new BusinessException(
                    "cursorMessageId는 1 이상이어야 합니다."
            );
        }
    }

    private record RoomDisplay(
            String name,
            String avatarUrl
    ) {
    }

    private record OpenSenderKey(
            Long roomId,
            Long userId
    ) {
    }
}
