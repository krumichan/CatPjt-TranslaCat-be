package jp.co.translacat.domain.user.friend.service;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.deletion.UserAccountWriteFence;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jp.co.translacat.domain.user.friend.dto.FriendResponseDto;
import jp.co.translacat.domain.user.friend.entity.Friend;
import jp.co.translacat.domain.user.friend.repository.FriendRepository;
import jp.co.translacat.domain.user.profile.dto.UserSummaryProfileResponseDto;
import jp.co.translacat.domain.user.profile.service.UserProfileQueryService;
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
    private final UserProfileQueryService userProfileQueryService;

    @PersistenceContext
    private EntityManager entities;

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
        // 양방향 요청도 같은 ID 순서로 잠가 탈퇴와 친구 관계 복원이 교차하지 않는다.
        User lower = UserAccountWriteFence.lock(entities, user1.getId() < user2.getId() ? user1 : user2);
        User higher = UserAccountWriteFence.lock(entities, user1.getId() < user2.getId() ? user2 : user1);
        return friendRepository.findByUserIds(user1.getId(), user2.getId())
                .map(this::restoreOrThrowIfActive)
                .orElseGet(() -> friendRepository.save(Friend.create(lower, higher)));
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

    public List<FriendResponseDto> getFriends(Long loginUserId) {
        return friendRepository.findActiveFriendsByUserId(loginUserId)
                .stream()
                .map(friend -> toResponse(friend, loginUserId))
                .toList();
    }

    @Transactional
    public void deleteFriend(
            Long loginUserId,
            Long friendUserId
    ) {
        Friend friend = getActiveFriend(loginUserId, friendUserId);
        friend.softDelete();
    }

    private FriendResponseDto toResponse(
            Friend friend,
            Long loginUserId
    ) {
        User friendUser = extractFriendUser(friend, loginUserId);
        UserSummaryProfileResponseDto friendProfile = userProfileQueryService.getSummaryByUser(friendUser);

        return FriendResponseDto.of(
                friend,
                friendProfile
        );
    }

    private User extractFriendUser(
            Friend friend,
            Long loginUserId
    ) {
        if (friend.getUserLow().getId().equals(loginUserId)) {
            return friend.getUserHigh();
        }

        if (friend.getUserHigh().getId().equals(loginUserId)) {
            return friend.getUserLow();
        }

        throw new BusinessException(
                "친구 관계에 포함된 사용자가 아닙니다.",
                "FRIEND_USER_NOT_INCLUDED"
        );
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
                .filter(User::isActiveAccount)
                .orElseThrow(() -> new BusinessException(
                        "사용자를 찾을 수 없습니다.",
                        "USER_NOT_FOUND"
                ));
    }
}
