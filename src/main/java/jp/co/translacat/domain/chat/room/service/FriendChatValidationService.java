package jp.co.translacat.domain.chat.room.service;

import jp.co.translacat.domain.user.block.service.UserBlockService;
import jp.co.translacat.domain.user.friend.service.FriendService;
import jp.co.translacat.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FriendChatValidationService {

    private final FriendService friendService;
    private final UserBlockService userBlockService;

    public void validateDirectTarget(
            Long loginUserId,
            Long friendUserId
    ) {
        validateLoginUser(loginUserId);
        validateTargetUser(loginUserId, friendUserId);

        validateFriendRelation(loginUserId, friendUserId);
        validateBlockRelation(loginUserId, friendUserId);
    }

    public void validateGroupMembers(
            Long loginUserId,
            List<Long> memberUserIds
    ) {
        validateLoginUser(loginUserId);

        Set<Long> distinctMemberUserIds = getDistinctMemberUserIds(memberUserIds);

        if (distinctMemberUserIds.isEmpty()) {
            throw new BusinessException(
                    "친구 그룹 채팅 멤버는 최소 1명 이상 필요합니다.",
                    "FRIEND_GROUP_MEMBER_REQUIRED"
            );
        }

        if (distinctMemberUserIds.contains(loginUserId)) {
            throw new BusinessException(
                    "자기 자신은 친구 그룹 채팅 멤버 목록에 포함할 수 없습니다.",
                    "FRIEND_GROUP_SELF_MEMBER_NOT_ALLOWED"
            );
        }

        for (Long memberUserId : distinctMemberUserIds) {
            validateTargetUser(loginUserId, memberUserId);
            validateFriendRelation(loginUserId, memberUserId);
            validateBlockRelation(loginUserId, memberUserId);
        }
    }

    private void validateLoginUser(Long loginUserId) {
        if (loginUserId == null) {
            throw new BusinessException(
                    "로그인이 필요합니다.",
                    "UNAUTHORIZED"
            );
        }
    }

    private void validateTargetUser(
            Long loginUserId,
            Long targetUserId
    ) {
        if (targetUserId == null) {
            throw new BusinessException(
                    "대상 사용자 ID는 필수입니다.",
                    "FRIEND_CHAT_TARGET_USER_ID_REQUIRED"
            );
        }

        if (loginUserId.equals(targetUserId)) {
            throw new BusinessException(
                    "자기 자신은 친구 채팅 대상이 될 수 없습니다.",
                    "FRIEND_CHAT_SELF_NOT_ALLOWED"
            );
        }
    }

    private void validateFriendRelation(
            Long loginUserId,
            Long targetUserId
    ) {
        if (!friendService.areFriends(loginUserId, targetUserId)) {
            throw new BusinessException(
                    "친구 관계인 사용자와만 친구 채팅을 시작할 수 있습니다.",
                    "FRIEND_RELATION_REQUIRED"
            );
        }
    }

    private void validateBlockRelation(
            Long loginUserId,
            Long targetUserId
    ) {
        if (userBlockService.isBlockedBetween(loginUserId, targetUserId)) {
            throw new BusinessException(
                    "차단 관계가 있는 사용자와 친구 채팅을 시작할 수 없습니다.",
                    "USER_BLOCKED_BETWEEN"
            );
        }
    }

    private Set<Long> getDistinctMemberUserIds(List<Long> memberUserIds) {
        if (memberUserIds == null || memberUserIds.isEmpty()) {
            return Set.of();
        }

        return new LinkedHashSet<>(memberUserIds);
    }
}
