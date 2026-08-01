package jp.co.translacat.domain.chat.openchat.ban.repository;

import jp.co.translacat.domain.chat.openchat.ban.entity.OpenChatBan;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface OpenChatBanRepositoryCustom {

    boolean existsActiveByRoomIdAndTargetUserId(
            Long roomId,
            Long targetUserId
    );

    Set<Long> findActiveBannedRoomIds(
            Long targetUserId,
            List<Long> roomIds
    );

    Optional<OpenChatBan> findActiveByRoomIdAndTargetUserIdForUpdate(
            Long roomId,
            Long targetUserId
    );

    Optional<OpenChatBan> findActiveByIdAndRoomIdForUpdate(
            Long banId,
            Long roomId
    );

    List<OpenChatBanQueryRow> findActivePage(
            Long roomId,
            String keyword,
            Long cursorId,
            int limit
    );
}
