package jp.co.translacat.domain.chat.ai.service;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import jp.co.translacat.domain.chat.room.repository.ChatRoomRepository;
import jp.co.translacat.domain.chat.ai.support.ChatAiErrorCode;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatAiAccessService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository memberRepository;

    public ChatRoom getAccessibleRoom(Long loginUserId, Long roomId) {
        ChatRoom room = chatRoomRepository
                .findByIdAndActiveTrueAndDeletedAtIsNull(roomId)
                .orElseThrow(this::roomNotFound);
        validateSupportedRoomType(room);
        getActiveHumanMember(loginUserId, roomId);
        return room;
    }

    public ChatRoomMember getActiveHumanMember(
            Long loginUserId,
            Long roomId
    ) {
        return memberRepository
                .findByChatRoomIdAndUserIdAndActiveTrueAndDeletedAtIsNull(
                        roomId,
                        loginUserId
                )
                .orElseThrow(() -> new BusinessException(
                        "채팅방 멤버가 아니거나 AI 설정 조회 권한이 없습니다.",
                        ChatAiErrorCode.ROOM_MEMBER_ACCESS_DENIED
                ));
    }

    public ChatRoom getManageableRoom(
            Long loginUserId,
            Long roomId
    ) {
        ChatRoom room = chatRoomRepository
                .findByIdAndActiveTrueAndDeletedAtIsNull(roomId)
                .orElseThrow(this::roomNotFound);
        validateSupportedRoomType(room);
        ChatRoomMember actor = getActiveHumanMember(loginUserId, roomId);
        validateManager(actor);
        return room;
    }

    @Transactional
    public ChatRoom getManageableRoomForUpdate(
            Long loginUserId,
            Long roomId
    ) {
        ChatRoom room = chatRoomRepository
                .findActiveByIdForUpdate(roomId)
                .orElseThrow(this::roomNotFound);
        validateSupportedRoomType(room);

        ChatRoomMember actor = memberRepository
                .findActiveByRoomIdAndUserIdForUpdate(roomId, loginUserId)
                .orElseThrow(() -> new BusinessException(
                        "채팅방 OWNER 또는 ADMIN만 AI 멤버를 관리할 수 있습니다.",
                        ChatAiErrorCode.ROOM_MANAGEMENT_ACCESS_DENIED
                ));

        validateManager(actor);
        return room;
    }

    private void validateManager(ChatRoomMember actor) {
        if (!actor.isOwner() && !actor.isAdmin()) {
            throw new BusinessException(
                    "채팅방 OWNER 또는 ADMIN만 AI 멤버를 관리할 수 있습니다.",
                    ChatAiErrorCode.ROOM_MANAGEMENT_ACCESS_DENIED
            );
        }
    }

    private void validateSupportedRoomType(ChatRoom room) {
        ChatRoomType roomType = room.getRoomType();
        if (roomType != ChatRoomType.GROUP
                && roomType != ChatRoomType.OPEN) {
            throw new BusinessException(
                    "AI 멤버는 GROUP 또는 OPEN 채팅방에서만 사용할 수 있습니다.",
                    ChatAiErrorCode.ROOM_TYPE_NOT_SUPPORTED
            );
        }
    }

    private BusinessException roomNotFound() {
        return new BusinessException(
                "활성 채팅방을 찾을 수 없습니다.",
                ChatAiErrorCode.ROOM_NOT_FOUND
        );
    }
}
