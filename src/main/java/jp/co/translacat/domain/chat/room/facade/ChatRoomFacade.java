package jp.co.translacat.domain.chat.room.facade;

import jp.co.translacat.domain.chat.room.dto.request.ChatRoomCreateRequestDto;
import jp.co.translacat.domain.chat.room.dto.response.ChatRoomListResponseDto;
import jp.co.translacat.domain.chat.room.dto.response.ChatRoomResponseDto;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.service.ChatRoomCommandService;
import jp.co.translacat.domain.chat.room.service.ChatRoomQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatRoomFacade {

    private final ChatRoomCommandService chatRoomCommandService;
    private final ChatRoomQueryService chatRoomQueryService;

    public ChatRoomResponseDto create(
            Long loginUserId,
            ChatRoomCreateRequestDto request
    ) {
        ChatRoom chatRoom = chatRoomCommandService.create(
                loginUserId,
                request
        );

        return chatRoomQueryService.getChatRoom(
                loginUserId,
                chatRoom.getId()
        );
    }

    public ChatRoomResponseDto createOrGetFriendDirectRoom(
            Long loginUserId,
            Long friendUserId
    ) {
        ChatRoom chatRoom = chatRoomCommandService.createOrGetFriendDirectRoom(
                loginUserId,
                friendUserId
        );

        return chatRoomQueryService.getChatRoom(
                loginUserId,
                chatRoom.getId()
        );
    }

    public ChatRoomListResponseDto getMyChatRooms(Long loginUserId) {
        return chatRoomQueryService.getMyChatRooms(loginUserId);
    }

    public ChatRoomResponseDto getChatRoom(
            Long loginUserId,
            Long chatRoomId
    ) {
        return chatRoomQueryService.getChatRoom(
                loginUserId,
                chatRoomId
        );
    }
}
