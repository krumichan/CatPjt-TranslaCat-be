package jp.co.translacat.domain.chat.ai.service;

import jp.co.translacat.domain.chat.ai.dto.request.ChatRoomAiSettingUpdateRequestDto;
import jp.co.translacat.domain.chat.ai.dto.response.ChatRoomAiSettingResponseDto;
import jp.co.translacat.domain.chat.ai.entity.ChatAiSystemSetting;
import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiSetting;
import jp.co.translacat.domain.chat.ai.enums.ChatAiDisclosureType;
import jp.co.translacat.domain.chat.ai.enums.ChatAiMentionPermission;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRoomAiSettingServiceTest {

    @Mock private ChatAiAccessService accessService;
    @Mock private ChatRoomAiSettingRepository settingRepository;
    @Mock private ChatRoomAiMemberRepository aiMemberRepository;
    @Mock private ChatAiSystemSettingService systemSettingService;

    private ChatRoomAiSettingService service;
    private ChatRoom room;

    @BeforeEach
    void setUp() {
        service = new ChatRoomAiSettingService(
                accessService,
                settingRepository,
                aiMemberRepository,
                systemSettingService
        );
        User owner = User.createLocalUser(
                "owner@ai-setting.test",
                "password",
                "owner",
                Role.USER,
                "AISETOWNER"
        );
        room = ChatRoom.createGroupRoom("group", "desc", owner);
        ReflectionTestUtils.setField(room, "id", 100L);
    }

    @Test
    void createsDefaultRoomSettingAndReturnsCurrentCount() {
        ChatAiSystemSetting systemSetting = ChatAiSystemSetting.createDefault();
        when(accessService.getAccessibleRoom(1L, 100L)).thenReturn(room);
        when(settingRepository.findByChatRoomId(100L))
                .thenReturn(Optional.empty());
        when(settingRepository.save(any(ChatRoomAiSetting.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(aiMemberRepository.countByChatRoomIdAndActiveTrueAndDeletedAtIsNull(100L))
                .thenReturn(1L);
        when(systemSettingService.getOrCreateEntity())
                .thenReturn(systemSetting);

        ChatRoomAiSettingResponseDto response = service.getSettings(1L, 100L);

        assertThat(response.aiEnabled()).isTrue();
        assertThat(response.currentAiMemberCount()).isEqualTo(1);
        assertThat(response.maxAiMembersPerRoom()).isEqualTo(2);
        assertThat(response.disclosureType())
                .isEqualTo(ChatAiDisclosureType.PUBLIC);
        assertThat(response.mentionPermission())
                .isEqualTo(ChatAiMentionPermission.ALL_MEMBERS);
    }

    @Test
    void ownerCanSwitchPrivateAndDisableAutonomousFeatures() {
        ChatAiSystemSetting systemSetting = ChatAiSystemSetting.createDefault();
        ChatRoomAiSetting setting = ChatRoomAiSetting.createDefault(room);
        when(accessService.getManageableRoomForUpdate(1L, 100L))
                .thenReturn(room);
        when(settingRepository.findByChatRoomId(100L))
                .thenReturn(Optional.of(setting));
        when(aiMemberRepository.countByChatRoomIdAndActiveTrueAndDeletedAtIsNull(100L))
                .thenReturn(0L);
        when(systemSettingService.getOrCreateEntity())
                .thenReturn(systemSetting);

        ChatRoomAiSettingResponseDto response = service.updateSettings(
                1L,
                100L,
                new ChatRoomAiSettingUpdateRequestDto(
                        ChatAiDisclosureType.PRIVATE,
                        ChatAiMentionPermission.OWNER_ADMIN_ONLY,
                        false,
                        false
                )
        );

        assertThat(response.disclosureType())
                .isEqualTo(ChatAiDisclosureType.PRIVATE);
        assertThat(response.mentionPermission())
                .isEqualTo(ChatAiMentionPermission.OWNER_ADMIN_ONLY);
        assertThat(response.conversationEnabled()).isFalse();
        assertThat(response.revivalEnabled()).isFalse();
    }
}
