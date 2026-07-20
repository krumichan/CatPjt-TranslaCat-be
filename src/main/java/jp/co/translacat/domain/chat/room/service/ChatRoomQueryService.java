package jp.co.translacat.domain.chat.room.service;

import jp.co.translacat.domain.chat.language.dto.ChatLanguageSettingResult;
import jp.co.translacat.domain.chat.language.service.ChatLanguageSettingResolver;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.room.dto.response.ChatRoomListItemResponseDto;
import jp.co.translacat.domain.chat.room.dto.response.ChatRoomListResponseDto;
import jp.co.translacat.domain.chat.room.dto.response.ChatRoomResponseDto;
import jp.co.translacat.domain.chat.room.dto.response.DirectPartnerProfileResponseDto;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomSourceType;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import jp.co.translacat.domain.chat.room.repository.ChatRoomRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.profile.entity.UserProfile;
import jp.co.translacat.domain.user.profile.repository.UserProfileRepository;
import jp.co.translacat.domain.user.profile.storage.service.UserProfileImageUrlResolver;
import jp.co.translacat.global.exception.BusinessException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomQueryService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatLanguageSettingResolver chatLanguageSettingResolver;
    private final UserProfileRepository userProfileRepository;
    private final UserProfileImageUrlResolver imageUrlResolver;

    public ChatRoomListResponseDto getMyChatRooms(
            Long loginUserId
    ) {
        List<ChatRoom> chatRooms =
                chatRoomMemberRepository
                        .findByUserIdAndActiveTrueAndDeletedAtIsNull(
                                loginUserId
                        )
                        .stream()
                        .map(ChatRoomMember::getChatRoom)
                        .filter(chatRoom ->
                                chatRoom.isActive()
                                        && !chatRoom.isDeleted()
                        )
                        .sorted(
                                Comparator.comparing(
                                        ChatRoom::getUpdatedAt
                                ).reversed()
                        )
                        .toList();

        List<Long> chatRoomIds = chatRooms.stream()
                .map(ChatRoom::getId)
                .toList();

        Map<Long, List<ChatRoomMember>>
                membersByRoomId =
                findMembersByRoomId(chatRoomIds);

        Map<Long, UserProfile> profilesByUserId =
                findProfilesByUserId(membersByRoomId);

        List<ChatRoomListItemResponseDto> responseItems =
                chatRooms.stream()
                        .map(chatRoom -> {
                            List<ChatRoomMember> members =
                                    membersByRoomId
                                            .getOrDefault(
                                                    chatRoom.getId(),
                                                    List.of()
                                            );

                            DirectPartnerProfileResponseDto
                                    directPartner =
                                    resolveDirectPartner(
                                            chatRoom,
                                            loginUserId,
                                            members,
                                            profilesByUserId
                                    );

                            return ChatRoomListItemResponseDto.from(
                                    chatRoom,
                                    members.size(),
                                    directPartner
                            );
                        })
                        .toList();

        return ChatRoomListResponseDto.from(
                responseItems
        );
    }

    public ChatRoomResponseDto getChatRoom(
            Long loginUserId,
            Long chatRoomId
    ) {
        ChatRoomMember chatRoomMember =
                chatRoomMemberRepository
                        .findByChatRoomIdAndUserIdAndActiveTrueAndDeletedAtIsNull(
                                chatRoomId,
                                loginUserId
                        )
                        .orElseThrow(() -> new BusinessException(
                                "채팅방에 접근할 권한이 없습니다."
                        ));

        ChatRoom chatRoom =
                chatRoomMember.getChatRoom();

        if (!chatRoom.isActive()
                || chatRoom.isDeleted()) {
            throw new BusinessException(
                    "채팅방을 찾을 수 없습니다."
            );
        }

        ChatLanguageSettingResult languageSetting =
                chatLanguageSettingResolver.resolve(
                        chatRoomMember
                );

        List<ChatRoomMember> members =
                chatRoomMemberRepository
                        .findByChatRoomIdAndActiveTrueAndDeletedAtIsNull(
                                chatRoom.getId()
                        );

        Map<Long, UserProfile> profilesByUserId =
                findProfilesByUserId(members);

        DirectPartnerProfileResponseDto directPartner =
                resolveDirectPartner(
                        chatRoom,
                        loginUserId,
                        members,
                        profilesByUserId
                );

        return ChatRoomResponseDto.from(
                chatRoom,
                languageSetting,
                members.size(),
                chatRoomMember.getRole(),
                directPartner
        );
    }

    public ChatRoom getAccessibleChatRoom(
            Long loginUserId,
            Long chatRoomId
    ) {
        ChatRoom chatRoom = chatRoomRepository
                .findByIdAndActiveTrueAndDeletedAtIsNull(
                        chatRoomId
                )
                .orElseThrow(() -> new BusinessException(
                        "채팅방을 찾을 수 없습니다."
                ));

        boolean accessible =
                chatRoomMemberRepository
                        .existsByChatRoomIdAndUserIdAndActiveTrueAndDeletedAtIsNull(
                                chatRoomId,
                                loginUserId
                        );

        if (!accessible) {
            throw new BusinessException(
                    "채팅방에 접근할 권한이 없습니다."
            );
        }

        return chatRoom;
    }

    private Map<Long, List<ChatRoomMember>>
    findMembersByRoomId(
            List<Long> chatRoomIds
    ) {
        if (chatRoomIds.isEmpty()) {
            return Map.of();
        }

        return chatRoomMemberRepository
                .findByChatRoomIdInAndActiveTrueAndDeletedAtIsNull(
                        chatRoomIds
                )
                .stream()
                .collect(Collectors.groupingBy(
                        member ->
                                member.getChatRoom().getId()
                ));
    }

    private Map<Long, UserProfile> findProfilesByUserId(
            Map<Long, List<ChatRoomMember>>
                    membersByRoomId
    ) {
        List<ChatRoomMember> members =
                membersByRoomId.values()
                        .stream()
                        .flatMap(List::stream)
                        .toList();

        return findProfilesByUserId(members);
    }

    private Map<Long, UserProfile> findProfilesByUserId(
            List<ChatRoomMember> members
    ) {
        List<Long> userIds = members.stream()
                .map(ChatRoomMember::getUser)
                .filter(Objects::nonNull)
                .map(User::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (userIds.isEmpty()) {
            return Map.of();
        }

        return userProfileRepository
                .findByUserIdInAndDeletedFalse(userIds)
                .stream()
                .filter(profile ->
                        profile.getUser() != null
                )
                .collect(Collectors.toMap(
                        profile ->
                                profile.getUser().getId(),
                        Function.identity(),
                        (left, right) -> left
                ));
    }

    private DirectPartnerProfileResponseDto
    resolveDirectPartner(
            ChatRoom chatRoom,
            Long loginUserId,
            List<ChatRoomMember> members,
            Map<Long, UserProfile> profilesByUserId
    ) {
        if (!isFriendDirectRoom(chatRoom)) {
            return null;
        }

        return members.stream()
                .map(ChatRoomMember::getUser)
                .filter(Objects::nonNull)
                .filter(user ->
                        !Objects.equals(
                                user.getId(),
                                loginUserId
                        )
                )
                .findFirst()
                .map(user ->
                        DirectPartnerProfileResponseDto.from(
                                user,
                                profilesByUserId.get(
                                        user.getId()
                                ),
                                imageUrlResolver
                        )
                )
                .orElse(null);
    }

    private boolean isFriendDirectRoom(
            ChatRoom chatRoom
    ) {
        return chatRoom.getRoomType()
                == ChatRoomType.DIRECT
                && chatRoom.getSourceType()
                == ChatRoomSourceType.FRIEND;
    }
}
