package jp.co.translacat.domain.chat.openchat.repository;

import jp.co.translacat.domain.chat.openchat.entity.OpenChatRoom;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface OpenChatRoomRepositoryCustom {

    List<OpenChatRoom> findPublicActivePage(
            String keyword,
            Long cursorId,
            int limit
    );

    Optional<OpenChatRoom> findByChatRoomId(Long chatRoomId);

    Optional<OpenChatRoom> findByChatRoomIdForUpdate(Long chatRoomId);

    Map<Long, Long> countActiveMembers(List<Long> chatRoomIds);

    Set<Long> findJoinedRoomIds(
            Long userId,
            List<Long> chatRoomIds
    );

    Set<Long> findActiveRoomIds(List<Long> chatRoomIds);

    Map<Long, OpenChatMemberProfileQueryRow> findOwnerProfiles(
            List<Long> chatRoomIds
    );

    Optional<OpenChatMemberProfileQueryRow> findMyProfile(
            Long chatRoomId,
            Long userId
    );

    Map<Long, LocalDateTime> findLastActivityAt(
            List<Long> chatRoomIds
    );
}
