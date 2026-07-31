package jp.co.translacat.domain.chat.openchat.service;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
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

    public ChatRoomMember getActiveOpenMember(
            Long userId,
            Long roomId
    ) {
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

    public void validateMessageSendAllowed(ChatRoomMember member) {
        if (member == null
                || member.getChatRoom() == null
                || member.getChatRoom().getRoomType()
                != ChatRoomType.OPEN) {
            return;
        }
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

    private BusinessException roomNotFound() {
        return new BusinessException(
                "OPEN 채팅방을 찾을 수 없습니다.",
                OpenChatErrorCode.ROOM_NOT_FOUND
        );
    }
}
