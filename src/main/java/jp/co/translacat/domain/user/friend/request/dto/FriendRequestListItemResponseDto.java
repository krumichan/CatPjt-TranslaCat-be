package jp.co.translacat.domain.user.friend.request.dto;

import jp.co.translacat.domain.user.friend.request.entity.FriendRequest;
import jp.co.translacat.domain.user.friend.request.enums.FriendRequestStatus;
import jp.co.translacat.domain.user.profile.dto.UserSummaryProfileResponseDto;

import java.time.LocalDateTime;

public record FriendRequestListItemResponseDto(
        Long id,
        UserSummaryProfileResponseDto requester,
        UserSummaryProfileResponseDto receiver,
        FriendRequestStatus status,
        LocalDateTime requestedAt,
        LocalDateTime respondedAt
) {

    public static FriendRequestListItemResponseDto of(
            FriendRequest friendRequest,
            UserSummaryProfileResponseDto requesterProfile,
            UserSummaryProfileResponseDto receiverProfile
    ) {
        return new FriendRequestListItemResponseDto(
                friendRequest.getId(),
                requesterProfile,
                receiverProfile,
                friendRequest.getStatus(),
                friendRequest.getRequestedAt(),
                friendRequest.getRespondedAt()
        );
    }
}
