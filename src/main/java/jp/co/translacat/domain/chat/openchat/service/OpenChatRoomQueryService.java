package jp.co.translacat.domain.chat.openchat.service;

import jp.co.translacat.domain.chat.ai.dto.response.ChatAiRoomSummaryResponseDto;
import jp.co.translacat.domain.chat.ai.service.ChatAiRoomSummaryService;
import jp.co.translacat.domain.chat.openchat.ban.repository.OpenChatBanRepository;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatMemberProfileResponseDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatRoomDetailResponseDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatRoomListItemResponseDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatRoomListResponseDto;
import jp.co.translacat.domain.chat.openchat.entity.OpenChatRoom;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatJoinBlockedReason;
import jp.co.translacat.domain.chat.openchat.profile.service.OpenChatProfileImageUrlResolver;
import jp.co.translacat.domain.chat.openchat.repository.OpenChatMemberProfileQueryRow;
import jp.co.translacat.domain.chat.openchat.repository.OpenChatRoomRepository;
import jp.co.translacat.domain.chat.openchat.support.OpenChatErrorCode;
import jp.co.translacat.domain.chat.openchat.support.OpenChatPolicy;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OpenChatRoomQueryService {

    private final OpenChatRoomRepository openChatRoomRepository;
    private final OpenChatBanRepository banRepository;
    private final OpenChatProfileImageUrlResolver imageUrlResolver;
    private final ChatAiRoomSummaryService aiRoomSummaryService;

    public OpenChatRoomListResponseDto getPublicRooms(
            Long loginUserId,
            String keyword,
            Long cursorId,
            Integer requestedSize
    ) {
        String normalizedKeyword = normalizeKeyword(keyword);
        validateCursor(cursorId);
        int size = normalizePageSize(requestedSize);

        List<OpenChatRoom> fetched =
                openChatRoomRepository.findPublicActivePage(
                        normalizedKeyword,
                        cursorId,
                        size + 1
                );

        boolean hasNext = fetched.size() > size;
        List<OpenChatRoom> pageRooms = hasNext
                ? new ArrayList<>(fetched.subList(0, size))
                : new ArrayList<>(fetched);

        List<Long> roomIds = pageRooms.stream()
                .map(OpenChatRoom::getChatRoom)
                .map(ChatRoom::getId)
                .toList();

        Map<Long, Long> memberCounts =
                openChatRoomRepository.countActiveMembers(roomIds);
        Set<Long> joinedRoomIds =
                openChatRoomRepository.findJoinedRoomIds(
                        loginUserId,
                        roomIds
                );
        Set<Long> bannedRoomIds =
                banRepository.findActiveBannedRoomIds(
                        loginUserId,
                        roomIds
                );
        Map<Long, OpenChatMemberProfileQueryRow> ownerProfiles =
                openChatRoomRepository.findOwnerProfiles(roomIds);
        Map<Long, LocalDateTime> lastActivityAtByRoomId =
                openChatRoomRepository.findLastActivityAt(roomIds);
        Map<Long, ChatAiRoomSummaryResponseDto> aiSummaries =
                resolveAiSummaries(roomIds);

        List<OpenChatRoomListItemResponseDto> items = pageRooms
                .stream()
                .map(openChatRoom -> toListItem(
                        openChatRoom,
                        memberCounts.getOrDefault(
                                openChatRoom.getChatRoom().getId(),
                                0L
                        ),
                        joinedRoomIds.contains(
                                openChatRoom.getChatRoom().getId()
                        ),
                        bannedRoomIds.contains(
                                openChatRoom.getChatRoom().getId()
                        ),
                        ownerProfiles.get(
                                openChatRoom.getChatRoom().getId()
                        ),
                        lastActivityAtByRoomId.get(
                                openChatRoom.getChatRoom().getId()
                        ),
                        aiSummaries.getOrDefault(
                                openChatRoom.getChatRoom().getId(),
                                ChatAiRoomSummaryResponseDto.disabled()
                        )
                ))
                .toList();

        Long nextCursorId = hasNext && !pageRooms.isEmpty()
                ? pageRooms.get(pageRooms.size() - 1)
                        .getChatRoom()
                        .getId()
                : null;

        return OpenChatRoomListResponseDto.of(
                items,
                nextCursorId,
                hasNext
        );
    }

    public OpenChatRoomDetailResponseDto getDetail(
            Long loginUserId,
            Long chatRoomId
    ) {
        if (chatRoomId == null || chatRoomId <= 0) {
            throw roomNotFound();
        }

        OpenChatRoom openChatRoom = openChatRoomRepository
                .findByChatRoomId(chatRoomId)
                .orElseThrow(this::roomNotFound);

        List<Long> roomIds = List.of(chatRoomId);
        long memberCount = openChatRoomRepository
                .countActiveMembers(roomIds)
                .getOrDefault(chatRoomId, 0L);
        boolean joined = openChatRoomRepository
                .findJoinedRoomIds(loginUserId, roomIds)
                .contains(chatRoomId);
        boolean banned = banRepository
                .existsActiveByRoomIdAndTargetUserId(
                        chatRoomId,
                        loginUserId
                );

        OpenChatMemberProfileQueryRow ownerProfile =
                openChatRoomRepository.findOwnerProfiles(roomIds)
                        .get(chatRoomId);
        Optional<OpenChatMemberProfileQueryRow> myProfile =
                openChatRoomRepository.findMyProfile(
                        chatRoomId,
                        loginUserId
                );
        Optional<OpenChatMemberProfileQueryRow> visibleMyProfile =
                banned ? Optional.empty() : myProfile;
        LocalDateTime lastActivityAt = openChatRoomRepository
                .findLastActivityAt(roomIds)
                .get(chatRoomId);
        ChatAiRoomSummaryResponseDto aiSummary =
                resolveAiSummaries(roomIds)
                        .getOrDefault(
                                chatRoomId,
                                ChatAiRoomSummaryResponseDto.disabled()
                        );

        OpenChatJoinBlockedReason blockedReason =
                resolveBlockedReason(
                        openChatRoom,
                        memberCount,
                        joined,
                        banned
                );

        return new OpenChatRoomDetailResponseDto(
                openChatRoom.getChatRoom().getId(),
                openChatRoom.getChatRoom().getRoomType(),
                openChatRoom.getChatRoom().getSourceType(),
                openChatRoom.getChatRoom().getName(),
                openChatRoom.getChatRoom().getDescription(),
                openChatRoom.getVisibility(),
                openChatRoom.getStatus(),
                memberCount,
                openChatRoom.getMaxMemberCount(),
                joined,
                blockedReason == OpenChatJoinBlockedReason.NONE,
                blockedReason,
                visibleMyProfile.filter(
                                OpenChatMemberProfileQueryRow::active
                        )
                        .map(OpenChatMemberProfileQueryRow::role)
                        .orElse(null),
                banned ? null : toProfileResponse(ownerProfile),
                visibleMyProfile.map(this::toProfileResponse)
                        .orElse(null),
                fallbackActivityAt(
                        openChatRoom.getChatRoom(),
                        lastActivityAt
                ),
                openChatRoom.getChatRoom().getCreatedAt(),
                openChatRoom.getChatRoom().getUpdatedAt(),
                aiSummary
        );
    }

    private OpenChatRoomListItemResponseDto toListItem(
            OpenChatRoom openChatRoom,
            long memberCount,
            boolean joined,
            boolean banned,
            OpenChatMemberProfileQueryRow ownerProfile,
            LocalDateTime lastActivityAt,
            ChatAiRoomSummaryResponseDto aiSummary
    ) {
        OpenChatJoinBlockedReason blockedReason =
                resolveBlockedReason(
                        openChatRoom,
                        memberCount,
                        joined,
                        banned
                );
        ChatRoom room = openChatRoom.getChatRoom();

        return new OpenChatRoomListItemResponseDto(
                room.getId(),
                room.getRoomType(),
                room.getSourceType(),
                room.getName(),
                room.getDescription(),
                openChatRoom.getVisibility(),
                openChatRoom.getStatus(),
                memberCount,
                openChatRoom.getMaxMemberCount(),
                joined,
                blockedReason == OpenChatJoinBlockedReason.NONE,
                blockedReason,
                fallbackActivityAt(room, lastActivityAt),
                toProfileResponse(ownerProfile),
                aiSummary
        );
    }


    private Map<Long, ChatAiRoomSummaryResponseDto> resolveAiSummaries(
            List<Long> roomIds
    ) {
        if (aiRoomSummaryService == null || roomIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ChatAiRoomSummaryResponseDto> summaries =
                aiRoomSummaryService.getSummaries(roomIds);
        return summaries == null ? Map.of() : summaries;
    }

    private OpenChatMemberProfileResponseDto toProfileResponse(
            OpenChatMemberProfileQueryRow row
    ) {
        if (row == null) {
            return null;
        }
        return new OpenChatMemberProfileResponseDto(
                row.openChatMemberId(),
                row.memberCode(),
                row.nickname(),
                imageUrlResolver.resolve(
                        row.profileImageObjectKey()
                ),
                row.role(),
                row.active(),
                row.joinedAt()
        );
    }

    private OpenChatJoinBlockedReason resolveBlockedReason(
            OpenChatRoom openChatRoom,
            long memberCount,
            boolean joined,
            boolean banned
    ) {
        if (banned) {
            return OpenChatJoinBlockedReason.BANNED;
        }
        if (openChatRoom.isClosed()) {
            return OpenChatJoinBlockedReason.ROOM_CLOSED;
        }
        if (joined) {
            return OpenChatJoinBlockedReason.ALREADY_JOINED;
        }
        if (memberCount >= openChatRoom.getMaxMemberCount()) {
            return OpenChatJoinBlockedReason.ROOM_FULL;
        }
        return OpenChatJoinBlockedReason.NONE;
    }

    private LocalDateTime fallbackActivityAt(
            ChatRoom room,
            LocalDateTime lastActivityAt
    ) {
        if (lastActivityAt != null) {
            return lastActivityAt;
        }
        if (room.getUpdatedAt() != null) {
            return room.getUpdatedAt();
        }
        return room.getCreatedAt();
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String normalized = keyword.trim();
        if (normalized.length()
                > OpenChatPolicy.MAX_KEYWORD_LENGTH) {
            throw new BusinessException(
                    "OPEN 채팅 검색어는 100자 이하여야 합니다.",
                    OpenChatErrorCode.KEYWORD_TOO_LONG
            );
        }
        return normalized;
    }

    private void validateCursor(Long cursorId) {
        if (cursorId != null && cursorId <= 0) {
            throw new BusinessException(
                    "cursorId는 1 이상이어야 합니다.",
                    OpenChatErrorCode.CURSOR_INVALID
            );
        }
    }

    private int normalizePageSize(Integer requestedSize) {
        int size = requestedSize == null
                ? OpenChatPolicy.DEFAULT_PAGE_SIZE
                : requestedSize;
        if (size <= 0 || size > OpenChatPolicy.MAX_PAGE_SIZE) {
            throw new BusinessException(
                    "size는 1 이상 50 이하여야 합니다.",
                    OpenChatErrorCode.PAGE_SIZE_INVALID
            );
        }
        return size;
    }

    private BusinessException roomNotFound() {
        return new BusinessException(
                "OPEN 채팅방을 찾을 수 없습니다.",
                OpenChatErrorCode.ROOM_NOT_FOUND
        );
    }
}
