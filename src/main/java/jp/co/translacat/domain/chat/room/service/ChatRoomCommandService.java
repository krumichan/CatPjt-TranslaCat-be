package jp.co.translacat.domain.chat.room.service;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.room.dto.request.ChatRoomCreateRequestDto;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomSourceType;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import jp.co.translacat.domain.chat.room.repository.ChatRoomRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.service.UserService;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatRoomCommandService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final UserService userService;
    private final FriendChatValidationService friendChatValidationService;

    public ChatRoom create(
            Long loginUserId,
            ChatRoomCreateRequestDto request
    ) {
        validateCreateRequest(loginUserId, request);

        User owner = userService.getById(loginUserId);

        if (request.roomType() == ChatRoomType.DIRECT) {
            return createOrGetDirectRoom(
                    loginUserId,
                    owner,
                    request
            );
        }

        if (request.roomType() == ChatRoomType.GROUP) {
            return createGroupRoom(owner, request);
        }

        throw new BusinessException("오픈 채팅방은 1차 MVP 생성 대상이 아닙니다.");
    }

    public ChatRoom createOrGetFriendDirectRoom(
            Long loginUserId,
            Long friendUserId
    ) {
        friendChatValidationService.validateDirectTarget(
                loginUserId,
                friendUserId
        );

        User owner = userService.getById(loginUserId);

        return chatRoomRepository
                .findActiveDirectRoomByUserIdsAndSourceType(
                        loginUserId,
                        friendUserId,
                        ChatRoomSourceType.FRIEND
                )
                .orElseGet(() -> createDirectRoom(
                        owner,
                        friendUserId,
                        ChatRoomSourceType.FRIEND
                ));
    }

    private ChatRoom createOrGetDirectRoom(
            Long loginUserId,
            User owner,
            ChatRoomCreateRequestDto request
    ) {
        Long targetUserId = getDistinctMemberUserIds(request.memberUserIds())
                .iterator()
                .next();

        ChatRoomSourceType sourceType = ChatRoomSourceType.MANUAL;

        return chatRoomRepository
                .findActiveDirectRoomByUserIdsAndSourceType(
                        loginUserId,
                        targetUserId,
                        sourceType
                )
                .orElseGet(() -> createDirectRoom(
                        owner,
                        targetUserId,
                        sourceType
                ));
    }

    private ChatRoom createDirectRoom(
            User owner,
            Long targetUserId,
            ChatRoomSourceType sourceType
    ) {
        User targetUser = userService.getById(targetUserId);

        ChatRoom chatRoom = ChatRoom.createDirectRoom(
                owner,
                sourceType
        );

        ChatRoom savedChatRoom = chatRoomRepository.save(chatRoom);

        chatRoomMemberRepository.save(
                ChatRoomMember.createOwner(
                        savedChatRoom,
                        owner,
                        null,
                        null
                )
        );

        chatRoomMemberRepository.save(
                ChatRoomMember.createMember(
                        savedChatRoom,
                        targetUser,
                        null,
                        null
                )
        );

        return savedChatRoom;
    }

    private ChatRoom createGroupRoom(
            User owner,
            ChatRoomCreateRequestDto request
    ) {
        ChatRoom chatRoom = ChatRoom.createGroupRoom(
                request.name(),
                request.description(),
                owner
        );

        ChatRoom savedChatRoom = chatRoomRepository.save(chatRoom);

        chatRoomMemberRepository.save(
                ChatRoomMember.createOwner(
                        savedChatRoom,
                        owner,
                        null,
                        null
                )
        );

        for (Long memberUserId : getDistinctMemberUserIds(request.memberUserIds())) {
            User memberUser = userService.getById(memberUserId);

            chatRoomMemberRepository.save(
                    ChatRoomMember.createMember(
                            savedChatRoom,
                            memberUser,
                            null,
                            null
                    )
            );
        }

        return savedChatRoom;
    }

    private void validateCreateRequest(
            Long loginUserId,
            ChatRoomCreateRequestDto request
    ) {
        if (request.roomType() == null) {
            throw new BusinessException("채팅방 타입은 필수입니다.");
        }

        if (request.roomType() == ChatRoomType.OPEN) {
            throw new BusinessException("오픈 채팅방은 1차 MVP 생성 대상이 아닙니다.");
        }

        List<Long> memberUserIds = request.memberUserIds();

        if (memberUserIds == null || memberUserIds.isEmpty()) {
            throw new BusinessException("채팅방 멤버는 최소 1명 이상 필요합니다.");
        }

        Set<Long> distinctMemberUserIds = getDistinctMemberUserIds(memberUserIds);

        if (distinctMemberUserIds.contains(loginUserId)) {
            throw new BusinessException("채팅방 생성자는 멤버 목록에 포함하지 않습니다.");
        }

        if (request.roomType() == ChatRoomType.DIRECT && distinctMemberUserIds.size() != 1) {
            throw new BusinessException("1:1 채팅방은 상대방 1명만 지정할 수 있습니다.");
        }

        if (request.roomType() == ChatRoomType.GROUP && distinctMemberUserIds.isEmpty()) {
            throw new BusinessException("그룹 채팅방은 최소 1명 이상의 멤버가 필요합니다.");
        }
    }

    private Set<Long> getDistinctMemberUserIds(List<Long> memberUserIds) {
        return new LinkedHashSet<>(memberUserIds);
    }
}
