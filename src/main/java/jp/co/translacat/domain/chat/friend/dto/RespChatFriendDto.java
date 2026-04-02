package jp.co.translacat.domain.chat.friend.dto;

import jp.co.translacat.domain.chat.friend.enums.FriendStatus;
import lombok.Getter;

@Getter
public class RespChatFriendDto {

    private String profileId;

    private String nickname;

    private String comment;

    private String iconPath;

    private String backgroundPath;

    private FriendStatus status;

    public static RespChatFriendDto of(ChatFriendDetailDto dto) {
        RespChatFriendDto res = new RespChatFriendDto();

        res.profileId = dto.profileId();
        res.nickname = dto.nickname();
        res.comment = dto.comment();
        res.iconPath = dto.iconPath();
        res.backgroundPath = dto.backgroundPath();
        res.status = dto.status();

        return res;
    }
}
