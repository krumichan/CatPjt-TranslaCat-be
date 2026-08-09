package jp.co.translacat.domain.chat.ai.service;

import jp.co.translacat.domain.chat.ai.entity.ChatAiAgent;
import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiMember;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatAiMentionParserTest {

    private final ChatAiMentionParser parser = new ChatAiMentionParser();

    @Test
    void findsMultipleExplicitAiMentionsWithoutPartialNicknameMatch() {
        ChatRoom room = createRoom();
        ChatRoomAiMember mika = ChatRoomAiMember.create(
                room,
                ChatAiAgent.create("Mika", null, "ja", "persona")
        );
        ChatRoomAiMember mi = ChatRoomAiMember.create(
                room,
                ChatAiAgent.create("Mi", null, "ko", "persona")
        );

        List<ChatRoomAiMember> result = parser.findMentionedMembers(
                "@Mika 안녕! @Mi도 같이 이야기해줘. @Mikaela는 다른 이름이야.",
                List.of(mika, mi)
        );

        assertThat(result).containsExactly(mika, mi);
    }

    @Test
    void mentionMatchingIsCaseInsensitive() {
        ChatRoom room = createRoom();
        ChatRoomAiMember mika = ChatRoomAiMember.create(
                room,
                ChatAiAgent.create("Mika", null, "ja", "persona")
        );

        assertThat(parser.findMentionedMembers(
                "@mIkA hello",
                List.of(mika)
        )).containsExactly(mika);
    }

    private ChatRoom createRoom() {
        User owner = User.createLocalUser(
                "mention-owner@test.local",
                "password",
                "owner",
                Role.USER,
                "MENTIONOWNER"
        );
        return ChatRoom.createGroupRoom("group", "desc", owner);
    }
}
