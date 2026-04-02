package jp.co.translacat.domain.chat.friend.repository;

import jp.co.translacat.domain.chat.friend.dto.ChatFriendDetailDto;
import jp.co.translacat.domain.chat.friend.enums.FriendStatus;

import java.util.List;

public interface FriendRepositoryCustom {
    List<ChatFriendDetailDto> findFriends(Long userId, FriendStatus status);
}
