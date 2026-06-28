package jp.co.translacat.domain.user.friend.request.dto;

import jp.co.translacat.domain.user.friend.request.entity.FriendRequest;
import jp.co.translacat.domain.user.friend.request.enums.FriendRequestStatus;
import jp.co.translacat.domain.user.profile.dto.UserSummaryProfileResponseDto;

import java.time.LocalDateTime;

public record FriendRequestResponseDto(
        Long id,
        Long requesterUserId,
        Long receiverUserId,
        String receiverPublicId,
        String receiverNickname,
        FriendRequestStatus status,
        LocalDateTime requestedAt,
        LocalDateTime respondedAt
) {

    public static FriendRequestResponseDto of(
            FriendRequest friendRequest,
            UserSummaryProfileResponseDto receiverProfile
    ) {
        return new FriendRequestResponseDto(
                friendRequest.getId(),
                friendRequest.getRequesterUser().getId(),
                friendRequest.getReceiverUser().getId(),
                receiverProfile.publicId(),
                receiverProfile.nickname(),
                friendRequest.getStatus(),
                friendRequest.getRequestedAt(),
                friendRequest.getRespondedAt()
        );
    }
}
