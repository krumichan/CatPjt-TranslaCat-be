package jp.co.translacat.domain.chat.openchat.profile.service;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatMessageSenderResponseDto;
import jp.co.translacat.domain.chat.openchat.profile.entity.OpenChatMemberProfile;
import jp.co.translacat.domain.chat.openchat.profile.repository.OpenChatMemberProfileRepository;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenChatMessageProfileServiceTest {

    @Mock private OpenChatMemberProfileRepository profileRepository;
    @Mock private OpenChatProfileImageUrlResolver imageUrlResolver;

    private OpenChatMessageProfileService service;
    private OpenChatMemberProfile profile;

    @BeforeEach
    void setUp() {
        service = new OpenChatMessageProfileService(
                profileRepository,
                imageUrlResolver
        );

        User user = User.createLocalUser(
                "sender@open.test",
                "password",
                "ordinary-name-must-not-be-used",
                Role.USER,
                "OPENSENDER1"
        );
        user.setId(2L);
        ChatRoom room = ChatRoom.createOpenRoom("open", "desc", user);
        ReflectionTestUtils.setField(room, "id", 100L);
        ChatRoomMember member = ChatRoomMember.createOwner(
                room,
                user,
                "ko",
                "ja"
        );
        ReflectionTestUtils.setField(member, "id", 20L);
        profile = OpenChatMemberProfile.create(
                member,
                "OC-ABCDE",
                "room-nickname",
                "open-chat-profiles/20/cat.png"
        );
    }

    @Test
    void resolvesOnlyRoomScopedSenderIdentity() {
        when(profileRepository
                .findByChatRoomMemberChatRoomIdAndChatRoomMemberUserId(
                        100L,
                        2L
                ))
                .thenReturn(Optional.of(profile));
        when(imageUrlResolver.resolve(
                "open-chat-profiles/20/cat.png"
        )).thenReturn("https://cdn.test/cat.png");

        OpenChatMessageSenderResponseDto result = service.resolve(
                100L,
                2L
        );

        assertThat(result.openChatMemberId()).isEqualTo(20L);
        assertThat(result.memberCode()).isEqualTo("OC-ABCDE");
        assertThat(result.nickname()).isEqualTo("room-nickname");
        assertThat(result.profileImageUrl())
                .isEqualTo("https://cdn.test/cat.png");
        assertThat(result.role()).isEqualTo(ChatRoomMemberRole.OWNER);
    }

    @Test
    void resolvesProfilesInOneBatchByUserId() {
        when(profileRepository
                .findByChatRoomMemberChatRoomIdAndChatRoomMemberUserIdIn(
                        100L,
                        Set.of(2L)
                ))
                .thenReturn(List.of(profile));
        when(imageUrlResolver.resolve(
                "open-chat-profiles/20/cat.png"
        )).thenReturn("https://cdn.test/cat.png");

        Map<Long, OpenChatMessageSenderResponseDto> result =
                service.resolveMap(100L, Arrays.asList(2L, 2L, null));

        assertThat(result).containsOnlyKeys(2L);
        assertThat(result.get(2L).nickname())
                .isEqualTo("room-nickname");
    }
}
