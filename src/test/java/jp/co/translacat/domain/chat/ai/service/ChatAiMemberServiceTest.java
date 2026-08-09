package jp.co.translacat.domain.chat.ai.service;

import jp.co.translacat.domain.chat.ai.dto.request.ChatAiMemberCreateRequestDto;
import jp.co.translacat.domain.chat.ai.dto.request.ChatAiMemberUpdateRequestDto;
import jp.co.translacat.domain.chat.ai.dto.response.ChatAiMemberResponseDto;
import jp.co.translacat.domain.chat.ai.entity.ChatAiAgent;
import jp.co.translacat.domain.chat.ai.entity.ChatAiSystemSetting;
import jp.co.translacat.domain.chat.ai.entity.ChatRoomAiMember;
import jp.co.translacat.domain.chat.ai.repository.ChatAiAgentRepository;
import jp.co.translacat.domain.chat.ai.repository.ChatRoomAiMemberRepository;
import jp.co.translacat.domain.chat.ai.support.ChatAiErrorCode;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatAiMemberServiceTest {

    @Mock private ChatAiAccessService accessService;
    @Mock private ChatAiAgentRepository agentRepository;
    @Mock private ChatRoomAiMemberRepository aiMemberRepository;
    @Mock private ChatRoomAiSettingService roomSettingService;
    @Mock private ChatAiSystemSettingService systemSettingService;
    @Mock private ChatAiProfileImageUrlResolver imageUrlResolver;

    private ChatAiMemberService service;
    private ChatRoom room;

    @BeforeEach
    void setUp() {
        service = new ChatAiMemberService(
                accessService,
                agentRepository,
                aiMemberRepository,
                roomSettingService,
                systemSettingService,
                imageUrlResolver
        );
        User owner = User.createLocalUser(
                "owner@ai.test",
                "password",
                "owner",
                Role.USER,
                "AIOWNER01"
        );
        room = ChatRoom.createGroupRoom("group", "desc", owner);
        ReflectionTestUtils.setField(room, "id", 100L);
    }

    @Test
    void createsAiMemberUnderSystemLimit() {
        ChatAiSystemSetting setting = ChatAiSystemSetting.createDefault();
        ChatAiMemberCreateRequestDto request = new ChatAiMemberCreateRequestDto(
                "Mika",
                "bio",
                "ja",
                "friendly persona"
        );

        when(accessService.getManageableRoomForUpdate(1L, 100L))
                .thenReturn(room);
        when(systemSettingService.getOrCreateEntity()).thenReturn(setting);
        when(aiMemberRepository.countByChatRoomIdAndActiveTrueAndDeletedAtIsNull(100L))
                .thenReturn(1L);
        when(agentRepository.save(any(ChatAiAgent.class)))
                .thenAnswer(invocation -> {
                    ChatAiAgent agent = invocation.getArgument(0);
                    ReflectionTestUtils.setField(agent, "id", 20L);
                    return agent;
                });
        when(aiMemberRepository.save(any(ChatRoomAiMember.class)))
                .thenAnswer(invocation -> {
                    ChatRoomAiMember member = invocation.getArgument(0);
                    ReflectionTestUtils.setField(member, "id", 30L);
                    return member;
                });

        ChatAiMemberResponseDto response = service.create(
                1L,
                100L,
                request
        );

        assertThat(response.aiMemberId()).isEqualTo(30L);
        assertThat(response.aiAgentId()).isEqualTo(20L);
        assertThat(response.nickname()).isEqualTo("Mika");
        verify(roomSettingService).getOrCreate(room);
    }

    @Test
    void rejectsCreateWhenSystemLimitIsReached() {
        ChatAiSystemSetting setting = ChatAiSystemSetting.createDefault();
        when(accessService.getManageableRoomForUpdate(1L, 100L))
                .thenReturn(room);
        when(systemSettingService.getOrCreateEntity()).thenReturn(setting);
        when(aiMemberRepository.countByChatRoomIdAndActiveTrueAndDeletedAtIsNull(100L))
                .thenReturn(2L);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.create(
                        1L,
                        100L,
                        new ChatAiMemberCreateRequestDto(
                                "Mika",
                                null,
                                "ja",
                                "persona"
                        )
                ))
                .satisfies(exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ChatAiErrorCode.MAX_MEMBER_COUNT_EXCEEDED));

        verify(agentRepository, never()).save(any());
        verify(aiMemberRepository, never()).save(any());
    }

    @Test
    void deleteSoftDeletesMembershipAndAgentButKeepsProfileFields() {
        ChatAiAgent agent = ChatAiAgent.create(
                "Mika",
                "bio",
                "ja",
                "persona"
        );
        ReflectionTestUtils.setField(agent, "id", 20L);
        ChatRoomAiMember member = ChatRoomAiMember.create(room, agent);
        ReflectionTestUtils.setField(member, "id", 30L);

        when(accessService.getManageableRoomForUpdate(1L, 100L))
                .thenReturn(room);
        when(aiMemberRepository.findByIdAndChatRoomIdAndActiveTrueAndDeletedAtIsNull(
                30L,
                100L
        )).thenReturn(Optional.of(member));

        ChatAiMemberResponseDto response = service.delete(1L, 100L, 30L);

        assertThat(member.isActive()).isFalse();
        assertThat(agent.isActive()).isFalse();
        assertThat(response.nickname()).isEqualTo("Mika");
    }

    @Test
    void updatesAiProfile() {
        ChatAiAgent agent = ChatAiAgent.create(
                "Mika",
                "bio",
                "ja",
                "persona"
        );
        ChatRoomAiMember member = ChatRoomAiMember.create(room, agent);
        ReflectionTestUtils.setField(member, "id", 30L);
        ReflectionTestUtils.setField(agent, "id", 20L);

        when(accessService.getManageableRoomForUpdate(1L, 100L))
                .thenReturn(room);
        when(aiMemberRepository.findByIdAndChatRoomIdAndActiveTrueAndDeletedAtIsNull(
                30L,
                100L
        )).thenReturn(Optional.of(member));

        ChatAiMemberResponseDto response = service.update(
                1L,
                100L,
                30L,
                new ChatAiMemberUpdateRequestDto(
                        "Mika 2",
                        "updated",
                        "ko",
                        "new persona"
                )
        );

        assertThat(response.nickname()).isEqualTo("Mika 2");
        assertThat(response.originalLanguageCode()).isEqualTo("ko");
        assertThat(response.personaPrompt()).isEqualTo("new persona");
    }
}
