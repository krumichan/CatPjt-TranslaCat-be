package jp.co.translacat.domain.chat.ai.service;

import jp.co.translacat.domain.chat.ai.dto.response.ChatAiDisplayMembersResponseDto;
import jp.co.translacat.domain.chat.ai.dto.response.ChatAiSafeProfileResponseDto;
import jp.co.translacat.domain.chat.ai.entity.ChatAiAgent;
import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiMember;
import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiSetting;
import jp.co.translacat.domain.chat.ai.enums.ChatAiDisclosureType;
import jp.co.translacat.domain.chat.ai.repository.ChatRoomAiMemberRepository;
import jp.co.translacat.domain.chat.ai.repository.ChatRoomAiSettingRepository;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatAiDisplayMemberServiceTest {

    @Mock private ChatAiAccessService accessService;
    @Mock private ChatRoomAiMemberRepository aiMemberRepository;
    @Mock private ChatRoomAiSettingRepository aiSettingRepository;
    @Mock private ChatAiProfileImageUrlResolver imageUrlResolver;

    private ChatAiDisplayMemberService service;
    private ChatRoom room;
    private ChatRoomAiMember aiMember;

    @BeforeEach
    void setUp() {
        service = new ChatAiDisplayMemberService(
                accessService,
                aiMemberRepository,
                aiSettingRepository,
                imageUrlResolver
        );

        User owner = User.createLocalUser(
                "owner@ai-display.test",
                "password",
                "owner",
                Role.USER,
                "AIDISPLAYOWNER"
        );
        room = ChatRoom.createGroupRoom("group", "desc", owner);
        ReflectionTestUtils.setField(room, "id", 100L);

        ChatAiAgent agent = ChatAiAgent.create(
                "Mika",
                "friendly bio",
                "ja",
                "private persona prompt"
        );
        ReflectionTestUtils.setField(agent, "id", 20L);
        aiMember = ChatRoomAiMember.create(room, agent);
        ReflectionTestUtils.setField(aiMember, "id", 30L);
    }

    @Test
    void displayMembersExposeOnlyRoomListFieldsWithDisclosurePolicy() {
        ChatRoomAiSetting setting = ChatRoomAiSetting.createDefault(room);
        setting.update(
                ChatAiDisclosureType.PRIVATE,
                setting.getMentionPermission(),
                setting.isConversationEnabled(),
                setting.isRevivalEnabled()
        );
        when(aiMemberRepository
                .findByChatRoomIdAndActiveTrueAndDeletedAtIsNullOrderByJoinedAtAsc(100L))
                .thenReturn(List.of(aiMember));
        when(aiSettingRepository.findByChatRoomId(100L))
                .thenReturn(Optional.of(setting));
        when(imageUrlResolver.resolveProfileImageUrl(aiMember.getAiAgent()))
                .thenReturn("https://cdn.test/mika.png");

        ChatAiDisplayMembersResponseDto result = service.getDisplayMembers(100L);

        assertThat(result.disclosureType()).isEqualTo(ChatAiDisclosureType.PRIVATE);
        assertThat(result.members()).hasSize(1);
        assertThat(result.members().getFirst().aiMemberId()).isEqualTo(30L);
        assertThat(result.members().getFirst().nickname()).isEqualTo("Mika");
        assertThat(result.members().getFirst().profileImageUrl())
                .isEqualTo("https://cdn.test/mika.png");
    }

    @Test
    void safeProfileAllowsAccessibleRoomMemberWithoutManagementFields() {
        when(aiMemberRepository
                .findByIdAndChatRoomIdAndActiveTrueAndDeletedAtIsNull(30L, 100L))
                .thenReturn(Optional.of(aiMember));
        when(imageUrlResolver.resolveProfileImageUrl(aiMember.getAiAgent()))
                .thenReturn("https://cdn.test/mika.png");
        when(imageUrlResolver.resolveProfileBackgroundImageUrl(aiMember.getAiAgent()))
                .thenReturn("https://cdn.test/mika-bg.png");

        ChatAiSafeProfileResponseDto result = service.getSafeProfile(
                1L,
                100L,
                30L
        );

        verify(accessService).getAccessibleRoom(1L, 100L);
        assertThat(result.aiMemberId()).isEqualTo(30L);
        assertThat(result.nickname()).isEqualTo("Mika");
        assertThat(result.bio()).isEqualTo("friendly bio");
        assertThat(result.originalLanguageCode()).isEqualTo("ja");
        assertThat(result.profileBackgroundImageUrl())
                .isEqualTo("https://cdn.test/mika-bg.png");
    }
}
