package jp.co.translacat.domain.chat.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.event.ChatRoomMemberInvitedApplicationEvent;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.notification.dto.response.ChatNotificationActivityItemResponseDto;
import jp.co.translacat.domain.chat.notification.entity.ChatNotification;
import jp.co.translacat.domain.chat.notification.enums.ChatNotificationType;
import jp.co.translacat.domain.chat.notification.event.ChatNotificationCreatedApplicationEvent;
import jp.co.translacat.domain.chat.notification.repository.ChatNotificationRepository;
import jp.co.translacat.domain.chat.openchat.event.OpenChatMemberBannedApplicationEvent;
import jp.co.translacat.domain.chat.openchat.event.OpenChatMemberRoleUpdatedApplicationEvent;
import jp.co.translacat.domain.chat.openchat.event.OpenChatRoomClosedApplicationEvent;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.repository.ChatRoomRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatNotificationActivityCreationService {

    private static final String OPEN_BAN_SOURCE_PREFIX = "open-ban:";
    private static final String OPEN_ROLE_SOURCE_PREFIX = "open-role:";
    private static final String OPEN_CLOSE_SOURCE_PREFIX = "open-close:";

    private final ChatNotificationRepository notificationRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final ChatNotificationActivityResponseMapper responseMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createInvitation(
            ChatRoomMemberInvitedApplicationEvent event
    ) {
        if (event == null) {
            return;
        }

        User recipient = userRepository.findById(event.recipientUserId())
                .orElse(null);
        ChatRoom room = chatRoomRepository.findById(event.roomId())
                .orElse(null);
        if (recipient == null || room == null) {
            log.warn(
                    "Skip chat invitation notification because source entity is missing. roomId={}, recipientUserId={}",
                    event.roomId(),
                    event.recipientUserId()
            );
            return;
        }

        User actor = resolveUser(event.actorUserId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roomName", room.getName());

        createIfAbsent(
                recipient,
                ChatNotificationType.CHAT_INVITATION,
                room,
                actor,
                payload,
                event.sourceEventKey()
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createOpenChatKicked(
            OpenChatMemberBannedApplicationEvent event
    ) {
        if (event == null) {
            return;
        }

        User recipient = userRepository.findByEmail(event.targetUsername())
                .orElse(null);
        ChatRoom room = chatRoomRepository.findById(event.roomId())
                .orElse(null);
        if (recipient == null || room == null) {
            log.warn(
                    "Skip OPEN kick notification because source entity is missing. roomId={}, targetUsername={}",
                    event.roomId(),
                    event.targetUsername()
            );
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roomName", room.getName());
        payload.put("reason", event.reason());
        payload.put("bannedAt", event.bannedAt());

        User actor = resolveUser(event.actorUserId());
        String sourceEventKey = event.banId() != null
                ? OPEN_BAN_SOURCE_PREFIX + event.banId()
                : OPEN_BAN_SOURCE_PREFIX
                + event.roomId()
                + ":"
                + event.targetOpenChatMemberId()
                + ":"
                + event.bannedAt();

        createIfAbsent(
                recipient,
                ChatNotificationType.OPEN_CHAT_KICKED,
                room,
                actor,
                payload,
                sourceEventKey
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createOpenChatRoleChanged(
            OpenChatMemberRoleUpdatedApplicationEvent event
    ) {
        if (event == null) {
            return;
        }

        ChatRoomMember target = memberRepository.findById(
                event.targetOpenChatMemberId()
        ).orElse(null);
        if (target == null
                || target.getChatRoom() == null
                || !target.getChatRoom().getId().equals(event.roomId())) {
            log.warn(
                    "Skip OPEN role notification because target member is missing. roomId={}, targetOpenChatMemberId={}",
                    event.roomId(),
                    event.targetOpenChatMemberId()
            );
            return;
        }

        ChatRoom room = target.getChatRoom();
        User recipient = target.getUser();
        if (event.actorUserId() != null
                && event.actorUserId().equals(recipient.getId())) {
            return;
        }
        User actor = resolveUser(event.actorUserId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roomName", room.getName());
        payload.put("newRole", event.role().name());

        createIfAbsent(
                recipient,
                ChatNotificationType.OPEN_CHAT_ROLE_CHANGED,
                room,
                actor,
                payload,
                OPEN_ROLE_SOURCE_PREFIX
                        + event.roomId()
                        + ":"
                        + event.targetOpenChatMemberId()
                        + ":"
                        + event.role().name()
                        + ":"
                        + event.occurredAt()
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createOpenChatRoomClosed(
            OpenChatRoomClosedApplicationEvent event
    ) {
        if (event == null) {
            return;
        }

        ChatRoom room = chatRoomRepository.findById(event.roomId())
                .orElse(null);
        if (room == null) {
            log.warn(
                    "Skip OPEN room closed notification because room is missing. roomId={}",
                    event.roomId()
            );
            return;
        }

        User actor = resolveUser(event.actorUserId());
        Long actorUserId = event.actorUserId();
        List<ChatRoomMember> activeMembers = memberRepository
                .findByChatRoomIdAndActiveTrueAndDeletedAtIsNull(
                        event.roomId()
                );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roomName", room.getName());
        payload.put("closedAt", event.closedAt());
        String sourceEventKey = OPEN_CLOSE_SOURCE_PREFIX
                + event.roomId()
                + ":"
                + event.closedAt();

        for (ChatRoomMember member : activeMembers) {
            User recipient = member.getUser();
            if (recipient == null
                    || (actorUserId != null
                    && actorUserId.equals(recipient.getId()))) {
                continue;
            }
            createIfAbsent(
                    recipient,
                    ChatNotificationType.OPEN_CHAT_ROOM_CLOSED,
                    room,
                    actor,
                    payload,
                    sourceEventKey
            );
        }
    }

    private void createIfAbsent(
            User recipient,
            ChatNotificationType type,
            ChatRoom room,
            User actor,
            Map<String, Object> payload,
            String sourceEventKey
    ) {
        if (notificationRepository
                .existsByRecipientUser_IdAndNotificationTypeAndSourceEventKey(
                        recipient.getId(),
                        type,
                        sourceEventKey
                )) {
            return;
        }

        ChatNotification saved = notificationRepository.saveAndFlush(
                ChatNotification.create(
                        recipient,
                        type,
                        room,
                        actor,
                        serializePayload(payload),
                        sourceEventKey
                )
        );
        ChatNotificationActivityItemResponseDto response =
                responseMapper.toResponse(saved);

        eventPublisher.publishEvent(
                ChatNotificationCreatedApplicationEvent.of(
                        recipient.getEmail(),
                        response
                )
        );
    }

    private User resolveUser(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).orElse(null);
    }

    private String serializePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(
                    payload != null ? payload : Map.of()
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "채팅 활동 알림 payload 직렬화에 실패했습니다.",
                    exception
            );
        }
    }
}
