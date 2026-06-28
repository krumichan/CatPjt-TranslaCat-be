package jp.co.translacat.domain.user.block.dto;

import jp.co.translacat.domain.user.block.entity.UserBlock;
import jp.co.translacat.domain.user.profile.dto.UserSummaryProfileResponseDto;

import java.time.LocalDateTime;

public record UserBlockResponseDto(
        Long id,
        UserSummaryProfileResponseDto blockedUser,
        LocalDateTime createdAt
) {

    public static UserBlockResponseDto of(
            UserBlock userBlock,
            UserSummaryProfileResponseDto blockedUserProfile
    ) {
        return new UserBlockResponseDto(
                userBlock.getId(),
                blockedUserProfile,
                userBlock.getCreatedAt()
        );
    }
}
