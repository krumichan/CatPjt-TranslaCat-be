package jp.co.translacat.domain.chat.room.service;

import jp.co.translacat.domain.chat.language.dto.ChatLanguageSettingResult;
import jp.co.translacat.domain.chat.language.service.ChatLanguageSettingResolver;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.room.dto.response.ChatRoomListItemResponseDto;
import jp.co.translacat.domain.chat.room.dto.response.ChatRoomListResponseDto;
import jp.co.translacat.domain.chat.room.dto.response.ChatRoomResponseDto;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.repository.ChatRoomRepository;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomQueryService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;

    private final ChatLanguageSettingResolver chatLanguageSettingResolver;

    public ChatRoomListResponseDto getMyChatRooms(Long loginUserId) {
        List<ChatRoomListItemResponseDto> chatRooms = chatRoomMemberRepository
                .findByUserIdAndActiveTrueAndDeletedAtIsNull(loginUserId)
                .stream()
                .map(ChatRoomMember::getChatRoom)
                .filter(chatRoom -> chatRoom.isActive() && !chatRoom.isDeleted())
                .sorted(Comparator.comparing(ChatRoom::getUpdatedAt).reversed())
                .map(ChatRoomListItemResponseDto::from)
                .toList();

        return ChatRoomListResponseDto.from(chatRooms);
    }

    public ChatRoomResponseDto getChatRoom(
            Long loginUserId,
            Long chatRoomId
    ) {
        ChatRoomMember chatRoomMember = chatRoomMemberRepository
                .findByChatRoomIdAndUserIdAndActiveTrueAndDeletedAtIsNull(
                        chatRoomId,
                        loginUserId
                )
                .orElseThrow(() -> new BusinessException("채팅방에 접근할 권한이 없습니다."));

        ChatRoom chatRoom = chatRoomMember.getChatRoom();

        if (!chatRoom.isActive() || chatRoom.isDeleted()) {
            throw new BusinessException("채팅방을 찾을 수 없습니다.");
        }

        ChatLanguageSettingResult languageSetting =
                chatLanguageSettingResolver.resolve(chatRoomMember);

        return ChatRoomResponseDto.from(chatRoom, languageSetting);
    }

    public ChatRoom getAccessibleChatRoom(
            Long loginUserId,
            Long chatRoomId
    ) {
        ChatRoom chatRoom = chatRoomRepository
                .findByIdAndActiveTrueAndDeletedAtIsNull(chatRoomId)
                .orElseThrow(() -> new BusinessException("채팅방을 찾을 수 없습니다."));

        boolean accessible = chatRoomMemberRepository
                .existsByChatRoomIdAndUserIdAndActiveTrueAndDeletedAtIsNull(
                        chatRoomId,
                        loginUserId
                );

        if (!accessible) {
            throw new BusinessException("채팅방에 접근할 권한이 없습니다.");
        }

        return chatRoom;
    }
}