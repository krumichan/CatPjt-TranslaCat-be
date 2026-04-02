package jp.co.translacat.domain.chat.friend.service;

import jp.co.translacat.domain.chat.friend.dto.ReqUpdateProfileDto;
import jp.co.translacat.domain.chat.friend.dto.RespChatFriendDto;
import jp.co.translacat.domain.chat.friend.entity.ChatProfile;
import jp.co.translacat.domain.chat.friend.enums.FriendStatus;
import jp.co.translacat.domain.chat.friend.repository.FriendRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.service.UserService;
import jp.co.translacat.global.utils.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FriendService {
    private final FriendRepository friendRepository;

    private final UserService userService;

    public List<RespChatFriendDto> friends(FriendStatus friendStatus) {
        User me = this.userService.findByEmail(SecurityUtil.getUsername());
        return this.friendRepository.findFriends(me.getId(), friendStatus)
            .stream().map(RespChatFriendDto::of).toList();
    }

    public Boolean checkActivation() {
        User me = this.userService.findByEmail(SecurityUtil.getUsername());
        return this.friendRepository.existsByUserId(me.getId());
    }

    @Transactional
    public Boolean updateProfile(ReqUpdateProfileDto requestDto) {
        // TODO: 장래에는 파일 저장하기 좋은 무료 뭐시기 찾으면 파일 저장할 것.
        User me = this.userService.findByEmail(SecurityUtil.getUsername());
        Optional<ChatProfile> maybeMyProfile = this.friendRepository.findByUserId(me.getId());
        if (maybeMyProfile.isPresent()) {
            maybeMyProfile.get().update(
                requestDto.getNickname(), requestDto.getComment(), null, null);
        } else {
            this.friendRepository.save(ChatProfile.create(
                me,
                requestDto.getNickname(),
                requestDto.getComment(),
                null,
                null
            ));
        }
        return true;
    }
}
