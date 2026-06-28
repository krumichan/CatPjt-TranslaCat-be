package jp.co.translacat.domain.user.friend.dto;

import jp.co.translacat.domain.user.friend.entity.Friend;
import jp.co.translacat.domain.user.profile.dto.UserSummaryProfileResponseDto;

import java.time.LocalDateTime;

public record FriendResponseDto(
        Long id,
        UserSummaryProfileResponseDto friend,
        LocalDateTime createdAt
) {

    public static FriendResponseDto of(
            Friend friend,
            UserSummaryProfileResponseDto friendProfile
    ) {
        return new FriendResponseDto(
                friend.getId(),
                friendProfile,
                friend.getCreatedAt()
        );
    }
}
