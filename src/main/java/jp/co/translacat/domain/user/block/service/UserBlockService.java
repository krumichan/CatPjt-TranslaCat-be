package jp.co.translacat.domain.user.block.service;

import jp.co.translacat.domain.user.block.entity.UserBlock;
import jp.co.translacat.domain.user.block.repository.UserBlockRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.friend.service.FriendService;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserBlockService {

    private final UserBlockRepository userBlockRepository;
    private final UserRepository userRepository;
    private final FriendService friendService;

    @Transactional
    public UserBlock blockUser(
            Long blockerUserId,
            Long blockedUserId
    ) {
        User blockerUser = getUser(blockerUserId);
        User blockedUser = getUser(blockedUserId);

        UserBlock userBlock = userBlockRepository.findByBlockerAndBlocked(
                        blockerUserId,
                        blockedUserId
                )
                .map(this::restoreOrThrowIfActive)
                .orElseGet(() -> userBlockRepository.save(UserBlock.create(
                        blockerUser,
                        blockedUser
                )));

        /*
         * 차단 시 친구 관계는 끊는 정책으로 처리한다.
         * 친구 관계가 없으면 무시한다.
         */
        if (friendService.areFriends(blockerUserId, blockedUserId)) {
            friendService.deleteFriend(blockerUserId, blockedUserId);
        }

        return userBlock;
    }

    @Transactional
    public void unblockUser(
            Long blockerUserId,
            Long blockedUserId
    ) {
        UserBlock userBlock = getActiveBlock(blockerUserId, blockedUserId);
        userBlock.softDelete();
    }

    public boolean isBlocking(
            Long blockerUserId,
            Long blockedUserId
    ) {
        if (blockerUserId == null || blockedUserId == null || blockerUserId.equals(blockedUserId)) {
            return false;
        }

        return userBlockRepository.existsActiveByBlockerAndBlocked(
                blockerUserId,
                blockedUserId
        );
    }

    public boolean isBlockedBetween(
            Long userId1,
            Long userId2
    ) {
        if (userId1 == null || userId2 == null || userId1.equals(userId2)) {
            return false;
        }

        return userBlockRepository.existsActiveBlockBetweenUsers(
                userId1,
                userId2
        );
    }

    public List<UserBlock> getActiveBlocksByBlockerUserId(Long blockerUserId) {
        return userBlockRepository.findActiveBlocksByBlockerUserId(blockerUserId);
    }

    public UserBlock getActiveBlock(
            Long blockerUserId,
            Long blockedUserId
    ) {
        return userBlockRepository.findActiveByBlockerAndBlocked(
                        blockerUserId,
                        blockedUserId
                )
                .orElseThrow(() -> new BusinessException(
                        "차단 관계를 찾을 수 없습니다.",
                        "USER_BLOCK_NOT_FOUND"
                ));
    }

    private UserBlock restoreOrThrowIfActive(UserBlock userBlock) {
        if (userBlock.isActive()) {
            throw new BusinessException(
                    "이미 차단한 사용자입니다.",
                    "USER_BLOCK_ALREADY_EXISTS"
            );
        }

        userBlock.restore();
        return userBlock;
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        "사용자를 찾을 수 없습니다.",
                        "USER_NOT_FOUND"
                ));
    }
}
