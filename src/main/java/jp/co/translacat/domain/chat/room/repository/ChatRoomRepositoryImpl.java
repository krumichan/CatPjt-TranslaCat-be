package jp.co.translacat.domain.chat.room.repository;

import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jp.co.translacat.domain.chat.member.entity.QChatRoomMember;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static jp.co.translacat.domain.chat.room.entity.QChatRoom.chatRoom;

@Repository
@RequiredArgsConstructor
public class ChatRoomRepositoryImpl {

    private final JPAQueryFactory queryFactory;

    public Optional<ChatRoom> findActiveDirectRoomByUserIds(
            Long userId1,
            Long userId2
    ) {
        QChatRoomMember loginMember = new QChatRoomMember("loginMember");
        QChatRoomMember targetMember = new QChatRoomMember("targetMember");
        QChatRoomMember activeMember = new QChatRoomMember("activeMember");

        ChatRoom result = queryFactory
                .select(chatRoom)
                .from(chatRoom)
                .join(loginMember).on(loginMember.chatRoom.eq(chatRoom))
                .join(targetMember).on(targetMember.chatRoom.eq(chatRoom))
                .where(
                        chatRoom.roomType.eq(ChatRoomType.DIRECT),
                        chatRoom.active.isTrue(),
                        chatRoom.deletedAt.isNull(),

                        loginMember.user.id.eq(userId1),
                        loginMember.active.isTrue(),
                        loginMember.deletedAt.isNull(),

                        targetMember.user.id.eq(userId2),
                        targetMember.active.isTrue(),
                        targetMember.deletedAt.isNull(),

                        JPAExpressions
                                .select(activeMember.id.count())
                                .from(activeMember)
                                .where(
                                        activeMember.chatRoom.eq(chatRoom),
                                        activeMember.active.isTrue(),
                                        activeMember.deletedAt.isNull()
                                )
                                .eq(2L)
                )
                .orderBy(chatRoom.id.asc())
                .fetchFirst();

        return Optional.ofNullable(result);
    }
}