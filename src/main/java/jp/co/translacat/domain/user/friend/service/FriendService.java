package jp.co.translacat.domain.user.friend.service;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.friend.entity.Friend;
import jp.co.translacat.domain.user.friend.repository.FriendRepository;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FriendService {

    private final FriendRepository friendRepository;
    private final UserRepository userRepository;

    @Transactional
    public Friend createFriend(
            Long userId1,
            Long userId2
    ) {
        User user1 = getUser(userId1);
        User user2 = getUser(userId2);

        return createFriend(user1, user2);
    }

    @Transactional
    public Friend createFriend(
            User user1,
            User user2
    ) {
        return friendRepository.findByUserIds(user1.getId(), user2.getId())
                .map(this::restoreOrThrowIfActive)
                .orElseGet(() -> friendRepository.save(Friend.create(user1, user2)));
    }

    public boolean areFriends(
            Long userId1,
            Long userId2
    ) {
        if (userId1 == null || userId2 == null || userId1.equals(userId2)) {
            return false;
        }

        return friendRepository.existsActiveByUserIds(userId1, userId2);
    }

    public Friend getActiveFriend(
            Long userId1,
            Long userId2
    ) {
        return friendRepository.findActiveByUserIds(userId1, userId2)
                .orElseThrow(() -> new BusinessException(
                        "친구 관계를 찾을 수 없습니다.",
                        "FRIEND_NOT_FOUND"
                ));
    }

    public List<Friend> getActiveFriendsByUserId(Long userId) {
        return friendRepository.findActiveFriendsByUserId(userId);
    }

    @Transactional
    public void deleteFriend(
            Long userId1,
            Long userId2
    ) {
        Friend friend = getActiveFriend(userId1, userId2);
        friend.softDelete();
    }

    private Friend restoreOrThrowIfActive(Friend friend) {
        if (friend.isActive()) {
            throw new BusinessException(
                    "이미 친구 관계입니다.",
                    "FRIEND_ALREADY_EXISTS"
            );
        }

        friend.restore();
        return friend;
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "사용자를 찾을 수 없습니다.",
                        "USER_NOT_FOUND"
                ));
    }
}
