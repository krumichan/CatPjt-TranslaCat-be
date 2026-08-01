package jp.co.translacat.domain.chat.openchat.service;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;
import jp.co.translacat.domain.chat.openchat.ban.repository.OpenChatBanQueryRow;
import jp.co.translacat.domain.chat.openchat.ban.repository.OpenChatBanRepository;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatBanActorResponseDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatBanListItemResponseDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatBanListResponseDto;
import jp.co.translacat.domain.chat.openchat.profile.service.OpenChatProfileImageUrlResolver;
import jp.co.translacat.domain.chat.openchat.support.OpenChatErrorCode;
import jp.co.translacat.domain.chat.openchat.support.OpenChatPolicy;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OpenChatBanQueryService {

    private final OpenChatBanRepository banRepository;
    private final OpenChatAccessService accessService;
    private final OpenChatProfileImageUrlResolver imageUrlResolver;

    public OpenChatBanListResponseDto getActiveBans(
            Long loginUserId,
            Long roomId,
            String keyword,
            Long cursorId,
            Integer requestedSize
    ) {
        ChatRoomMember actor = accessService.getActiveOpenMember(
                loginUserId,
                roomId
        );
        validateModerator(actor);

        String normalizedKeyword = normalizeKeyword(keyword);
        validateCursor(cursorId);
        int size = normalizePageSize(requestedSize);

        List<OpenChatBanQueryRow> fetched =
                banRepository.findActivePage(
                        roomId,
                        normalizedKeyword,
                        cursorId,
                        size + 1
                );

        boolean hasNext = fetched.size() > size;
        List<OpenChatBanQueryRow> pageRows = hasNext
                ? new ArrayList<>(fetched.subList(0, size))
                : new ArrayList<>(fetched);

        List<OpenChatBanListItemResponseDto> items = pageRows
                .stream()
                .map(row -> toResponse(actor, row))
                .toList();

        Long nextCursorId = hasNext && !pageRows.isEmpty()
                ? pageRows.get(pageRows.size() - 1).banId()
                : null;

        return OpenChatBanListResponseDto.of(
                items,
                nextCursorId,
                hasNext
        );
    }

    private OpenChatBanListItemResponseDto toResponse(
            ChatRoomMember actor,
            OpenChatBanQueryRow row
    ) {
        return new OpenChatBanListItemResponseDto(
                row.banId(),
                row.targetOpenChatMemberId(),
                row.memberCode(),
                row.nickname(),
                imageUrlResolver.resolve(
                        row.profileImageObjectKey()
                ),
                row.lastJoinedAt(),
                row.bannedAt(),
                new OpenChatBanActorResponseDto(
                        row.bannedByOpenChatMemberId(),
                        row.bannedByNickname(),
                        row.bannedByRole()
                ),
                row.reason(),
                isReleasable(actor, row)
        );
    }

    private boolean isReleasable(
            ChatRoomMember actor,
            OpenChatBanQueryRow row
    ) {
        if (actor.isOwner()) {
            return true;
        }
        return actor.isAdmin()
                && row.bannedByRole() == ChatRoomMemberRole.ADMIN
                && row.targetRoleSnapshot()
                == ChatRoomMemberRole.MEMBER;
    }

    private void validateModerator(ChatRoomMember actor) {
        if (!actor.isOwner() && !actor.isAdmin()) {
            throw new BusinessException(
                    "OPEN 채팅방 OWNER 또는 ADMIN만 블랙리스트를 조회할 수 있습니다.",
                    OpenChatErrorCode.MODERATION_ACCESS_DENIED
            );
        }
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String normalized = keyword.trim();
        if (normalized.length()
                > OpenChatPolicy.MAX_KEYWORD_LENGTH) {
            throw new BusinessException(
                    "OPEN 블랙리스트 검색어는 100자 이하여야 합니다.",
                    OpenChatErrorCode.KEYWORD_TOO_LONG
            );
        }
        return normalized;
    }

    private void validateCursor(Long cursorId) {
        if (cursorId != null && cursorId <= 0) {
            throw new BusinessException(
                    "cursor는 1 이상이어야 합니다.",
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
}
