package jp.co.translacat.domain.chat.friend.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jp.co.translacat.domain.chat.friend.dto.ChatFriendDetailDto;
import jp.co.translacat.domain.chat.friend.dto.QChatFriendDetailDto;
import jp.co.translacat.domain.chat.friend.enums.FriendStatus;
import lombok.RequiredArgsConstructor;

import java.util.List;

import static jp.co.translacat.domain.chat.friend.entity.QChatFriend.chatFriend;
import static jp.co.translacat.domain.chat.friend.entity.QChatProfile.chatProfile;

@RequiredArgsConstructor
public class FriendRepositoryImpl implements FriendRepositoryCustom {

    private final JPAQueryFactory factory;
    private final EntityManager entityManager;

    @Override
    public List<ChatFriendDetailDto> findFriends(Long userId, FriendStatus status) {
        return this.factory
                .select(new QChatFriendDetailDto(
                        chatProfile.id,
                        chatProfile.nickname,
                        chatProfile.comment,
                        chatProfile.iconPath,
                        chatProfile.backgroundPath,
                        chatFriend.status
                ))
                .from(chatProfile)
                .innerJoin(chatFriend).on(chatFriend.userProfile.id.eq(chatProfile.id))
                .where(chatProfile.user.id.eq(userId),
                       chatFriend.status.eq(status),
                       chatFriend.status.ne(FriendStatus.DELETED))
                .orderBy(chatProfile.nickname.asc())
                .fetch();
    }
}
