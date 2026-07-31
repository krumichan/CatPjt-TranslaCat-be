package jp.co.translacat.domain.chat.member.repository;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;

import java.util.Optional;

public interface ChatRoomMemberRepositoryCustom {

    Optional<ChatRoomMember> findActiveByRoomIdAndUserIdForUpdate(
            Long chatRoomId,
            Long userId
    );
}
