package jp.co.translacat.domain.chat.friend.dto;

import com.querydsl.core.annotations.QueryProjection;
import io.swagger.v3.oas.annotations.media.Schema;
import jp.co.translacat.domain.chat.friend.enums.FriendStatus;

public record ChatFriendDetailDto(@Schema(description = "", example = "") String profileId,
                                  @Schema(description = "", example = "") String nickname,
                                  @Schema(description = "", example = "") String comment,
                                  @Schema(description = "", example = "") String iconPath,
                                  @Schema(description = "", example = "") String backgroundPath,
                                  @Schema(description = "", example = "") FriendStatus status) {
    @QueryProjection
    public ChatFriendDetailDto { }
}
