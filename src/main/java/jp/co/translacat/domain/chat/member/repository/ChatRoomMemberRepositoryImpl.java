package jp.co.translacat.domain.chat.member.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static jp.co.translacat.domain.chat.member.entity.QChatRoomMember.chatRoomMember;

@Repository
@RequiredArgsConstructor
public class ChatRoomMemberRepositoryImpl
        implements ChatRoomMemberRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<ChatRoomMember> findActiveByRoomIdAndUserIdForUpdate(
            Long chatRoomId,
            Long userId
    ) {
        ChatRoomMember result = queryFactory
                .selectFrom(chatRoomMember)
                .where(
                        chatRoomMember.chatRoom.id.eq(chatRoomId),
                        chatRoomMember.user.id.eq(userId),
                        chatRoomMember.active.isTrue(),
                        chatRoomMember.deletedAt.isNull()
                )
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne();

        return Optional.ofNullable(result);
    }
}
