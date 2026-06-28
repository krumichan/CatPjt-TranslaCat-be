package jp.co.translacat.domain.user.friend.request.repository;

import jp.co.translacat.domain.user.friend.request.entity.FriendRequest;
import jp.co.translacat.domain.user.friend.request.enums.FriendRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendRequestRepository
        extends JpaRepository<FriendRequest, Long>, FriendRequestRepositoryCustom {

    Optional<FriendRequest> findByIdAndDeletedFalse(Long id);

    List<FriendRequest> findAllByReceiverUserIdAndStatusAndDeletedFalseOrderByRequestedAtDesc(
            Long receiverUserId,
            FriendRequestStatus status
    );

    List<FriendRequest> findAllByRequesterUserIdAndStatusAndDeletedFalseOrderByRequestedAtDesc(
            Long requesterUserId,
            FriendRequestStatus status
    );
}