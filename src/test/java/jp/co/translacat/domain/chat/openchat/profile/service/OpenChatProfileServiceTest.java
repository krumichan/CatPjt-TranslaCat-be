package jp.co.translacat.domain.chat.openchat.profile.service;

import jp.co.translacat.domain.chat.ai.dto.response.ChatAiDisplayMembersResponseDto;
import jp.co.translacat.domain.chat.ai.enums.ChatAiDisclosureType;
import jp.co.translacat.domain.chat.ai.service.ChatAiDisplayMemberService;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.openchat.dto.request.OpenChatProfileUpdateRequestDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatMemberListResponseDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatMemberProfileResponseDto;
import jp.co.translacat.domain.chat.openchat.event.OpenChatProfileUpdatedApplicationEvent;
import jp.co.translacat.domain.chat.openchat.profile.entity.OpenChatMemberProfile;
import jp.co.translacat.domain.chat.openchat.profile.repository.OpenChatMemberProfileRepository;
import jp.co.translacat.domain.chat.openchat.service.OpenChatAccessService;
import jp.co.translacat.domain.chat.openchat.support.OpenChatProfileValidator;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.presence.service.ChatPresenceQueryService;
import jp.co.translacat.domain.chat.presence.service.ChatPresenceVisibilityPolicy;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenChatProfileServiceTest {

    @Mock private OpenChatAccessService accessService;
    @Mock private ChatAiDisplayMemberService chatAiDisplayMemberService;
    @Mock private OpenChatMemberProfileRepository profileRepository;
    @Mock private OpenChatProfileResponseMapper responseMapper;
    @Mock private OpenChatProfileValidator profileValidator;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ChatPresenceQueryService chatPresenceQueryService;
    @Mock private ChatPresenceVisibilityPolicy chatPresenceVisibilityPolicy;

    private OpenChatProfileService service;
    private ChatRoom chatRoom;
    private ChatRoomMember member;
    private OpenChatMemberProfile profile;

    @BeforeEach
    void setUp() {
        service = new OpenChatProfileService(
                accessService,
                chatAiDisplayMemberService,
                profileRepository,
                responseMapper,
                profileValidator,
                eventPublisher,
                chatPresenceQueryService,
                chatPresenceVisibilityPolicy
        );

        User user = User.createLocalUser(
                "profile@open.test",
                "password",
                "profile-user",
                Role.USER,
                "OPENPROF01"
        );
        user.setId(10L);
        chatRoom = ChatRoom.createOpenRoom("open", "desc", user);
        ReflectionTestUtils.setField(chatRoom, "id", 100L);
        member = ChatRoomMember.createOwner(
                chatRoom,
                user,
                "ko",
                "ja"
        );
        ReflectionTestUtils.setField(member, "id", 20L);
        ReflectionTestUtils.setField(
                member,
                "joinedAt",
                LocalDateTime.of(2026, 8, 1, 10, 0)
        );
        profile = OpenChatMemberProfile.create(
                member,
                "OC-ABCDE",
                "before",
                null
        );
    }

    @Test
    void returnsOnlyActiveRoomProfilesForMemberList() {
        OpenChatMemberProfileResponseDto mapped = response("before");

        when(accessService.getActiveOpenMember(10L, 100L))
                .thenReturn(member);
        when(profileRepository
                .findByChatRoomMemberChatRoomIdAndChatRoomMemberActiveTrueAndChatRoomMemberDeletedAtIsNullOrderByChatRoomMemberJoinedAtAsc(
                        100L
                ))
                .thenReturn(List.of(profile));
        when(chatPresenceVisibilityPolicy.isVisible(100L)).thenReturn(true);
        when(chatPresenceQueryService.resolveOnlineByUserIds(List.of(10L)))
                .thenReturn(java.util.Map.of(10L, true));
        when(responseMapper.toResponse(profile, true)).thenReturn(mapped);
        when(chatAiDisplayMemberService.getDisplayMembers(100L))
                .thenReturn(ChatAiDisplayMembersResponseDto.empty());

        OpenChatMemberListResponseDto result = service.getMembers(
                10L,
                100L
        );

        assertThat(result.members()).containsExactly(mapped);
        verify(responseMapper).toResponse(profile, true);
    }


    @Test
    void privateAiRoomHidesHumanPresenceSnapshot() {
        OpenChatMemberProfileResponseDto mapped = response("before");

        when(accessService.getActiveOpenMember(10L, 100L))
                .thenReturn(member);
        when(profileRepository
                .findByChatRoomMemberChatRoomIdAndChatRoomMemberActiveTrueAndChatRoomMemberDeletedAtIsNullOrderByChatRoomMemberJoinedAtAsc(
                        100L
                ))
                .thenReturn(List.of(profile));
        when(chatPresenceVisibilityPolicy.isVisible(100L)).thenReturn(false);
        when(responseMapper.toResponse(profile, null)).thenReturn(mapped);
        when(chatAiDisplayMemberService.getDisplayMembers(100L))
                .thenReturn(new ChatAiDisplayMembersResponseDto(
                        ChatAiDisclosureType.PRIVATE,
                        List.of()
                ));

        OpenChatMemberListResponseDto result = service.getMembers(
                10L,
                100L
        );

        assertThat(result.members()).containsExactly(mapped);
        assertThat(result.aiDisclosureType())
                .isEqualTo(ChatAiDisclosureType.PRIVATE);
        verifyNoInteractions(chatPresenceQueryService);
        verify(responseMapper).toResponse(profile, null);
    }

    @Test
    void updatesNicknameAndPublishesRoomScopedEvent() {
        OpenChatMemberProfileResponseDto mapped = response("after");

        when(accessService.getActiveOpenMember(10L, 100L))
                .thenReturn(member);
        when(profileRepository.findByChatRoomMemberId(20L))
                .thenReturn(Optional.of(profile));
        when(profileValidator.normalizeNickname(" after "))
                .thenReturn("after");
        when(responseMapper.toResponse(profile)).thenReturn(mapped);

        OpenChatMemberProfileResponseDto result =
                service.updateMyProfile(
                        10L,
                        100L,
                        new OpenChatProfileUpdateRequestDto(" after ")
                );

        assertThat(result).isSameAs(mapped);
        assertThat(profile.getNickname()).isEqualTo("after");

        ArgumentCaptor<OpenChatProfileUpdatedApplicationEvent> captor =
                ArgumentCaptor.forClass(
                        OpenChatProfileUpdatedApplicationEvent.class
                );
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().roomId()).isEqualTo(100L);
        assertThat(captor.getValue().openChatMemberId()).isEqualTo(20L);
        assertThat(captor.getValue().memberCode())
                .isEqualTo("OC-ABCDE");
        assertThat(captor.getValue().nickname()).isEqualTo("after");
    }

    private OpenChatMemberProfileResponseDto response(String nickname) {
        return new OpenChatMemberProfileResponseDto(
                20L,
                "OC-ABCDE",
                nickname,
                null,
                member.getRole(),
                true,
                member.getJoinedAt()
        );
    }
}
