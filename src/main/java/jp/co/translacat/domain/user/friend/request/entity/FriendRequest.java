package jp.co.translacat.domain.user.friend.request.entity;

import jakarta.persistence.*;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.friend.request.enums.FriendRequestStatus;
import jp.co.translacat.global.exception.BusinessException;
import jp.co.translacat.global.jpa.BaseAuditable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(
        name = "friend_request",
        indexes = {
                @Index(name = "idx_friend_request_requester_user_id", columnList = "requester_user_id"),
                @Index(name = "idx_friend_request_receiver_user_id", columnList = "receiver_user_id"),
                @Index(name = "idx_friend_request_status", columnList = "status")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FriendRequest extends BaseAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_user_id", nullable = false, updatable = false)
    private User requesterUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_user_id", nullable = false, updatable = false)
    private User receiverUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FriendRequestStatus status;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private FriendRequest(
            User requesterUser,
            User receiverUser
    ) {
        validateUsers(requesterUser, receiverUser);

        this.requesterUser = requesterUser;
        this.receiverUser = receiverUser;
        this.status = FriendRequestStatus.PENDING;
        this.requestedAt = LocalDateTime.now();
    }

    public static FriendRequest create(
            User requesterUser,
            User receiverUser
    ) {
        return new FriendRequest(requesterUser, receiverUser);
    }

    public void accept() {
        validatePending();
        this.status = FriendRequestStatus.ACCEPTED;
        this.respondedAt = LocalDateTime.now();
    }

    public void reject() {
        validatePending();
        this.status = FriendRequestStatus.REJECTED;
        this.respondedAt = LocalDateTime.now();
    }

    public void cancel() {
        validatePending();
        this.status = FriendRequestStatus.CANCELED;
        this.respondedAt = LocalDateTime.now();
    }

    public void softDelete() {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isPending() {
        return this.status == FriendRequestStatus.PENDING;
    }

    public boolean isRequestedBy(Long userId) {
        return this.requesterUser.getId().equals(userId);
    }

    public boolean isReceivedBy(Long userId) {
        return this.receiverUser.getId().equals(userId);
    }

    private void validatePending() {
        if (!isPending()) {
            throw new BusinessException(
                    "이미 처리된 친구 요청입니다.",
                    "FRIEND_REQUEST_ALREADY_PROCESSED"
            );
        }
    }

    private static void validateUsers(
            User requesterUser,
            User receiverUser
    ) {
        if (requesterUser == null || receiverUser == null) {
            throw new BusinessException(
                    "친구 요청 사용자를 찾을 수 없습니다.",
                    "USER_NOT_FOUND"
            );
        }

        if (requesterUser.getId() != null && requesterUser.getId().equals(receiverUser.getId())) {
            throw new BusinessException(
                    "자기 자신에게 친구 요청을 보낼 수 없습니다.",
                    "FRIEND_REQUEST_SELF_NOT_ALLOWED"
            );
        }
    }
}
