package jp.co.translacat.domain.user.friend.entity;

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
        name = "friend",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_friend_user_low_user_high",
                        columnNames = {"user_low_id", "user_high_id"}
                )
        },
        indexes = {
                @Index(name = "idx_friend_user_low_id", columnList = "user_low_id"),
                @Index(name = "idx_friend_user_high_id", columnList = "user_high_id"),
                @Index(name = "idx_friend_deleted", columnList = "deleted")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Friend extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 친구 관계의 중복 생성을 막기 위해 작은 userId를 userLow로 저장한다.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_low_id", nullable = false, updatable = false)
    private User userLow;

    /**
     * 친구 관계의 중복 생성을 막기 위해 큰 userId를 userHigh로 저장한다.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_high_id", nullable = false, updatable = false)
    private User userHigh;

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private Friend(
            User user1,
            User user2
    ) {
        validateUsers(user1, user2);

        if (user1.getId() < user2.getId()) {
            this.userLow = user1;
            this.userHigh = user2;
        } else {
            this.userLow = user2;
            this.userHigh = user1;
        }
    }

    public static Friend create(
            User user1,
            User user2
    ) {
        return new Friend(user1, user2);
    }

    public boolean isBetween(
            Long userId1,
            Long userId2
    ) {
        return containsUser(userId1) && containsUser(userId2);
    }

    public boolean containsUser(Long userId) {
        if (userId == null) {
            return false;
        }

        return userLow.getId().equals(userId) || userHigh.getId().equals(userId);
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
            User user1,
            User user2
    ) {
        if (user1 == null || user2 == null) {
            throw new BusinessException(
                    "친구 관계 사용자를 찾을 수 없습니다.",
                    "USER_NOT_FOUND"
            );
        }

        if (user1.getId() == null || user2.getId() == null) {
            throw new BusinessException(
                    "친구 관계 사용자 ID가 필요합니다.",
                    "USER_ID_REQUIRED"
            );
        }

        if (user1.getId().equals(user2.getId())) {
            throw new BusinessException(
                    "자기 자신과 친구 관계를 생성할 수 없습니다.",
                    "FRIEND_SELF_NOT_ALLOWED"
            );
        }
    }
}
