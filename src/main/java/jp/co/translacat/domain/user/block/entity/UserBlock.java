package jp.co.translacat.domain.user.block.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "user_block",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_user_block_blocker_blocked",
                        columnNames = {"blocker_user_id", "blocked_user_id"}
                )
        },
        indexes = {
                @Index(name = "idx_user_block_blocker_user_id", columnList = "blocker_user_id"),
                @Index(name = "idx_user_block_blocked_user_id", columnList = "blocked_user_id"),
                @Index(name = "idx_user_block_deleted", columnList = "deleted")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBlock extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 차단한 사용자
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocker_user_id", nullable = false, updatable = false)
    private User blockerUser;

    /**
     * 차단된 사용자
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_user_id", nullable = false, updatable = false)
    private User blockedUser;

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private UserBlock(
            User blockerUser,
            User blockedUser
    ) {
        validateUsers(blockerUser, blockedUser);

        this.blockerUser = blockerUser;
        this.blockedUser = blockedUser;
    }

    public static UserBlock create(
            User blockerUser,
            User blockedUser
    ) {
        return new UserBlock(
                blockerUser,
                blockedUser
        );
    }

    public boolean isBlockedBetween(
            Long userId1,
            Long userId2
    ) {
        return isBlockerAndBlocked(userId1, userId2)
                || isBlockerAndBlocked(userId2, userId1);
    }

    public boolean isBlockerAndBlocked(
            Long blockerUserId,
            Long blockedUserId
    ) {
        if (blockerUserId == null || blockedUserId == null) {
            return false;
        }

        return blockerUser.getId().equals(blockerUserId)
                && blockedUser.getId().equals(blockedUserId);
    }

    public boolean isBlockedBy(Long blockerUserId) {
        if (blockerUserId == null) {
            return false;
        }

        return blockerUser.getId().equals(blockerUserId);
    }

    public boolean isActive() {
        return !deleted;
    }

    public void softDelete() {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    public void restore() {
        this.deleted = false;
        this.deletedAt = null;
    }

    private static void validateUsers(
            User blockerUser,
            User blockedUser
    ) {
        if (blockerUser == null || blockedUser == null) {
            throw new BusinessException(
                    "차단 관계 사용자를 찾을 수 없습니다.",
                    "USER_NOT_FOUND"
            );
        }

        if (blockerUser.getId() == null || blockedUser.getId() == null) {
            throw new BusinessException(
                    "차단 관계 사용자 ID가 필요합니다.",
                    "USER_ID_REQUIRED"
            );
        }

        if (blockerUser.getId().equals(blockedUser.getId())) {
            throw new BusinessException(
                    "자기 자신을 차단할 수 없습니다.",
                    "USER_BLOCK_SELF_NOT_ALLOWED"
            );
        }
    }
}
