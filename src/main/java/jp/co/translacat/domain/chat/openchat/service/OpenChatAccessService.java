package jp.co.translacat.domain.chat.openchat.service;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.openchat.ban.repository.OpenChatBanRepository;
import jp.co.translacat.domain.chat.openchat.entity.OpenChatRoom;
import jp.co.translacat.domain.chat.openchat.repository.OpenChatRoomRepository;
import jp.co.translacat.domain.chat.openchat.support.OpenChatErrorCode;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OpenChatAccessService {

    private final ChatRoomMemberRepository memberRepository;
    private final OpenChatRoomRepository openChatRoomRepository;
    private final OpenChatBanRepository banRepository;

    public ChatRoomMember getActiveOpenMember(
            Long userId,
            Long roomId
    ) {
        validateNotBanned(userId, roomId);

        ChatRoomMember member = memberRepository
                .findByChatRoomIdAndUserIdAndActiveTrueAndDeletedAtIsNull(
                        roomId,
                        userId
                )
                .orElseThrow(() -> new BusinessException(
                        "OPEN 채팅방 멤버가 아니거나 접근 권한이 없습니다.",
                        OpenChatErrorCode.MEMBER_ACCESS_DENIED
                ));

        if (member.getChatRoom().getRoomType() != ChatRoomType.OPEN) {
            throw roomNotFound();
        }
        return member;
    }

    public OpenChatRoom getOpenRoom(Long roomId) {
        return openChatRoomRepository.findByChatRoomId(roomId)
                .orElseThrow(this::roomNotFound);
    }

    public boolean isBanned(Long userId, Long roomId) {
        if (userId == null || roomId == null) {
            return false;
        }
        return banRepository.existsActiveByRoomIdAndTargetUserId(
                roomId,
                userId
        );
    }

    public void validateNotBanned(Long userId, Long roomId) {
        if (isBanned(userId, roomId)) {
            throw banned();
        }
    }

    /**
     * DIRECT/GROUP에는 영향을 주지 않고 OPEN 방에만 차단·멤버십 검증을 적용한다.
     */
    public void validateOpenRoomMemberAccess(
            Long userId,
            Long roomId
    ) {
        if (openChatRoomRepository.findByChatRoomId(roomId).isEmpty()) {
            return;
        }
        getActiveOpenMember(userId, roomId);
    }

    public void validateWebSocketAccess(
            Long userId,
            Long roomId
    ) {
        if (openChatRoomRepository.findByChatRoomId(roomId).isEmpty()) {
            return;
        }
        getActiveOpenMember(userId, roomId);
        validateRoomActive(roomId);
    }

    public void validateMessageSendAllowed(ChatRoomMember member) {
        if (member == null
                || member.getChatRoom() == null
                || member.getChatRoom().getRoomType()
                != ChatRoomType.OPEN) {
            return;
        }
        validateNotBanned(
                member.getUser().getId(),
                member.getChatRoom().getId()
        );
        validateRoomActive(member.getChatRoom().getId());
    }

    public OpenChatRoom validateProfileEditAllowed(
            Long userId,
            Long roomId
    ) {
        getActiveOpenMember(userId, roomId);
        return validateRoomActive(roomId);
    }

    public OpenChatRoom validateRoomActive(Long roomId) {
        OpenChatRoom openChatRoom = getOpenRoom(roomId);
        if (openChatRoom.isClosed()) {
            throw new BusinessException(
                    "종료된 OPEN 채팅방에서는 해당 작업을 수행할 수 없습니다.",
                    OpenChatErrorCode.ROOM_CLOSED
            );
        }
        return openChatRoom;
    }

    private BusinessException banned() {
        return new BusinessException(
                "해당 OPEN 채팅방에서 차단되어 접근할 수 없습니다.",
                OpenChatErrorCode.BANNED
        );
    }

    private BusinessException roomNotFound() {
        return new BusinessException(
                "OPEN 채팅방을 찾을 수 없습니다.",
                OpenChatErrorCode.ROOM_NOT_FOUND
        );
    }
}
