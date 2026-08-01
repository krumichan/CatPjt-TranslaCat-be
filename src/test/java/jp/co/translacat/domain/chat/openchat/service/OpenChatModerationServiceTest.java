package jp.co.translacat.domain.chat.openchat.service;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.repository.ChatMessageRepository;
import jp.co.translacat.domain.chat.openchat.ban.entity.OpenChatBan;
import jp.co.translacat.domain.chat.openchat.ban.repository.OpenChatBanRepository;
import jp.co.translacat.domain.chat.openchat.dto.request.OpenChatBanCreateRequestDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatMemberProfileResponseDto;
import jp.co.translacat.domain.chat.openchat.entity.OpenChatRoom;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatVisibility;
import jp.co.translacat.domain.chat.openchat.event.OpenChatMemberBannedApplicationEvent;
import jp.co.translacat.domain.chat.openchat.event.OpenChatMemberRoleUpdatedApplicationEvent;
import jp.co.translacat.domain.chat.openchat.profile.entity.OpenChatMemberProfile;
import jp.co.translacat.domain.chat.openchat.profile.repository.OpenChatMemberProfileRepository;
import jp.co.translacat.domain.chat.openchat.profile.service.OpenChatProfileResponseMapper;
import jp.co.translacat.domain.chat.openchat.repository.OpenChatRoomRepository;
import jp.co.translacat.domain.chat.openchat.support.OpenChatErrorCode;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenChatModerationServiceTest {

    @Mock private OpenChatRoomRepository openChatRoomRepository;
    @Mock private ChatRoomMemberRepository memberRepository;
    @Mock private OpenChatMemberProfileRepository profileRepository;
    @Mock private OpenChatBanRepository banRepository;
    @Mock private ChatMessageRepository messageRepository;
    @Mock private OpenChatProfileResponseMapper profileResponseMapper;
    @Mock private ApplicationEventPublisher eventPublisher;

    private OpenChatModerationService service;
    private ChatRoom chatRoom;
    private OpenChatRoom openChatRoom;
    private ChatRoomMember ownerMember;
    private ChatRoomMember adminMember;
    private ChatRoomMember targetMember;
    private OpenChatMemberProfile targetProfile;

    @BeforeEach
    void setUp() {
        service = new OpenChatModerationService(
                openChatRoomRepository,
                memberRepository,
                profileRepository,
                banRepository,
                messageRepository,
                profileResponseMapper,
                eventPublisher
        );

        User owner = user(1L, "owner@open.test", "OPENOWNER1");
        User admin = user(2L, "admin@open.test", "OPENADMIN1");
        User target = user(3L, "target@open.test", "OPENTARGET1");

        chatRoom = ChatRoom.createOpenRoom("open", "desc", owner);
        ReflectionTestUtils.setField(chatRoom, "id", 100L);
        openChatRoom = OpenChatRoom.create(
                chatRoom,
                OpenChatVisibility.PUBLIC,
                50
        );

        ownerMember = member(10L, owner, ChatRoomMemberRole.OWNER);
        adminMember = member(20L, admin, ChatRoomMemberRole.ADMIN);
        targetMember = member(30L, target, ChatRoomMemberRole.MEMBER);
        targetProfile = OpenChatMemberProfile.create(
                targetMember,
                "OC-TARGET",
                "같은고양이",
                "open-chat-profiles/30/avatar.png"
        );

        when(openChatRoomRepository.findByChatRoomIdForUpdate(100L))
                .thenReturn(Optional.of(openChatRoom));
    }

    @Test
    void ownerAssignsAndRevokesAdminAfterCommitEventRegistration() {
        OpenChatMemberProfileResponseDto response =
                mock(OpenChatMemberProfileResponseDto.class);
        when(memberRepository.findActiveByRoomIdAndUserIdForUpdate(
                100L,
                1L
        )).thenReturn(Optional.of(ownerMember));
        when(memberRepository.findActiveByIdAndRoomIdForUpdate(
                30L,
                100L
        )).thenReturn(Optional.of(targetMember));
        when(profileRepository.findByChatRoomMemberId(30L))
                .thenReturn(Optional.of(targetProfile));
        when(profileResponseMapper.toResponse(targetProfile))
                .thenReturn(response);

        assertThat(service.assignAdmin(1L, 100L, 30L))
                .isSameAs(response);
        assertThat(targetMember.getRole())
                .isEqualTo(ChatRoomMemberRole.ADMIN);
        verify(eventPublisher).publishEvent(
                any(OpenChatMemberRoleUpdatedApplicationEvent.class)
        );

        clearInvocations(eventPublisher);
        assertThat(service.revokeAdmin(1L, 100L, 30L))
                .isSameAs(response);
        assertThat(targetMember.getRole())
                .isEqualTo(ChatRoomMemberRole.MEMBER);
        verify(eventPublisher).publishEvent(
                any(OpenChatMemberRoleUpdatedApplicationEvent.class)
        );
    }

    @Test
    void adminCannotAssignAdmin() {
        when(memberRepository.findActiveByRoomIdAndUserIdForUpdate(
                100L,
                2L
        )).thenReturn(Optional.of(adminMember));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.assignAdmin(
                        2L,
                        100L,
                        30L
                ))
                .satisfies(exception -> assertThat(
                        exception.getErrorCode()
                ).isEqualTo(OpenChatErrorCode.OWNER_ONLY));
    }

    @Test
    void adminCannotRevokeAdmin() {
        when(memberRepository.findActiveByRoomIdAndUserIdForUpdate(
                100L,
                2L
        )).thenReturn(Optional.of(adminMember));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.revokeAdmin(
                        2L,
                        100L,
                        30L
                ))
                .satisfies(exception -> assertThat(
                        exception.getErrorCode()
                ).isEqualTo(OpenChatErrorCode.OWNER_ONLY));

        verify(memberRepository, never())
                .findActiveByIdAndRoomIdForUpdate(30L, 100L);
    }

    @Test
    void ownerBansAdminAtomicallyAndCreatesSystemMessage() {
        targetMember.changeRole(ChatRoomMemberRole.ADMIN);
        when(memberRepository.findActiveByRoomIdAndUserIdForUpdate(
                100L,
                1L
        )).thenReturn(Optional.of(ownerMember));
        when(memberRepository.findActiveByIdAndRoomIdForUpdate(
                30L,
                100L
        )).thenReturn(Optional.of(targetMember));
        when(profileRepository.findByChatRoomMemberId(30L))
                .thenReturn(Optional.of(targetProfile));
        when(banRepository
                .findActiveByRoomIdAndTargetUserIdForUpdate(
                        100L,
                        3L
                ))
                .thenReturn(Optional.empty());
        when(banRepository.save(any(OpenChatBan.class)))
                .thenAnswer(invocation -> {
                    OpenChatBan ban = invocation.getArgument(0);
                    ReflectionTestUtils.setField(ban, "id", 70L);
                    return ban;
                });
        when(messageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.ban(
                1L,
                100L,
                new OpenChatBanCreateRequestDto(
                        30L,
                        " 반복적인 도배 "
                )
        );

        assertThat(targetMember.isActive()).isFalse();
        assertThat(targetMember.getRole())
                .isEqualTo(ChatRoomMemberRole.MEMBER);
        assertThat(targetMember.getLastReadMessageId()).isNull();

        ArgumentCaptor<OpenChatBan> banCaptor =
                ArgumentCaptor.forClass(OpenChatBan.class);
        verify(banRepository).save(banCaptor.capture());
        assertThat(banCaptor.getValue().getTargetMemberCode())
                .isEqualTo("OC-TARGET");
        assertThat(banCaptor.getValue().getNicknameSnapshot())
                .isEqualTo("같은고양이");
        assertThat(banCaptor.getValue().getTargetRoleSnapshot())
                .isEqualTo(ChatRoomMemberRole.ADMIN);
        assertThat(banCaptor.getValue().getReason())
                .isEqualTo("반복적인 도배");

        ArgumentCaptor<ChatMessage> messageCaptor =
                ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository).save(messageCaptor.capture());
        assertThat(messageCaptor.getValue().isSystemMessage())
                .isTrue();
        assertThat(messageCaptor.getValue().getContent())
                .contains("OC-TARGET");

        verify(eventPublisher).publishEvent(
                any(OpenChatMemberBannedApplicationEvent.class)
        );
    }

    @Test
    void ownerCanBanActiveMember() {
        when(memberRepository.findActiveByRoomIdAndUserIdForUpdate(
                100L,
                1L
        )).thenReturn(Optional.of(ownerMember));
        when(memberRepository.findActiveByIdAndRoomIdForUpdate(
                30L,
                100L
        )).thenReturn(Optional.of(targetMember));
        when(profileRepository.findByChatRoomMemberId(30L))
                .thenReturn(Optional.of(targetProfile));
        when(banRepository
                .findActiveByRoomIdAndTargetUserIdForUpdate(
                        100L,
                        3L
                ))
                .thenReturn(Optional.empty());
        when(banRepository.save(any(OpenChatBan.class)))
                .thenAnswer(invocation -> {
                    OpenChatBan ban = invocation.getArgument(0);
                    ReflectionTestUtils.setField(ban, "id", 74L);
                    return ban;
                });
        when(messageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.ban(
                1L,
                100L,
                new OpenChatBanCreateRequestDto(30L, "reason")
        );

        assertThat(targetMember.isActive()).isFalse();
        ArgumentCaptor<OpenChatBan> captor =
                ArgumentCaptor.forClass(OpenChatBan.class);
        verify(banRepository).save(captor.capture());
        assertThat(captor.getValue().getBannedByRole())
                .isEqualTo(ChatRoomMemberRole.OWNER);
        assertThat(captor.getValue().getTargetRoleSnapshot())
                .isEqualTo(ChatRoomMemberRole.MEMBER);
    }

    @Test
    void adminCanBanActiveMember() {
        when(memberRepository.findActiveByRoomIdAndUserIdForUpdate(
                100L,
                2L
        )).thenReturn(Optional.of(adminMember));
        when(memberRepository.findActiveByIdAndRoomIdForUpdate(
                30L,
                100L
        )).thenReturn(Optional.of(targetMember));
        when(profileRepository.findByChatRoomMemberId(30L))
                .thenReturn(Optional.of(targetProfile));
        when(banRepository
                .findActiveByRoomIdAndTargetUserIdForUpdate(
                        100L,
                        3L
                ))
                .thenReturn(Optional.empty());
        when(banRepository.save(any(OpenChatBan.class)))
                .thenAnswer(invocation -> {
                    OpenChatBan ban = invocation.getArgument(0);
                    ReflectionTestUtils.setField(ban, "id", 73L);
                    return ban;
                });
        when(messageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.ban(
                2L,
                100L,
                new OpenChatBanCreateRequestDto(30L, "reason")
        );

        assertThat(targetMember.isActive()).isFalse();
        ArgumentCaptor<OpenChatBan> captor =
                ArgumentCaptor.forClass(OpenChatBan.class);
        verify(banRepository).save(captor.capture());
        assertThat(captor.getValue().getBannedByRole())
                .isEqualTo(ChatRoomMemberRole.ADMIN);
        assertThat(captor.getValue().getTargetRoleSnapshot())
                .isEqualTo(ChatRoomMemberRole.MEMBER);
    }

    @Test
    void rejectsDuplicateActiveBanBeforeCreatingHistory() {
        when(memberRepository.findActiveByRoomIdAndUserIdForUpdate(
                100L,
                1L
        )).thenReturn(Optional.of(ownerMember));
        when(memberRepository.findActiveByIdAndRoomIdForUpdate(
                30L,
                100L
        )).thenReturn(Optional.of(targetMember));
        when(banRepository
                .findActiveByRoomIdAndTargetUserIdForUpdate(
                        100L,
                        3L
                ))
                .thenReturn(Optional.of(ban(
                        ownerMember,
                        ChatRoomMemberRole.OWNER,
                        ChatRoomMemberRole.MEMBER
                )));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.ban(
                        1L,
                        100L,
                        new OpenChatBanCreateRequestDto(30L, "reason")
                ))
                .satisfies(exception -> assertThat(
                        exception.getErrorCode()
                ).isEqualTo(OpenChatErrorCode.BAN_ALREADY_ACTIVE));

        verify(banRepository, never()).save(any());
        verify(messageRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(
                any(OpenChatMemberBannedApplicationEvent.class)
        );
    }

    @Test
    void adminCannotBanAdminOrOwner() {
        targetMember.changeRole(ChatRoomMemberRole.ADMIN);
        when(memberRepository.findActiveByRoomIdAndUserIdForUpdate(
                100L,
                2L
        )).thenReturn(Optional.of(adminMember));
        when(memberRepository.findActiveByIdAndRoomIdForUpdate(
                30L,
                100L
        )).thenReturn(Optional.of(targetMember));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.ban(
                        2L,
                        100L,
                        new OpenChatBanCreateRequestDto(30L, "reason")
                ))
                .satisfies(exception -> assertThat(
                        exception.getErrorCode()
                ).isEqualTo(OpenChatErrorCode.BAN_ROLE_FORBIDDEN));

        verify(banRepository, never()).save(any());
    }

    @Test
    void adminCannotBanOwner() {
        when(memberRepository.findActiveByRoomIdAndUserIdForUpdate(
                100L,
                2L
        )).thenReturn(Optional.of(adminMember));
        when(memberRepository.findActiveByIdAndRoomIdForUpdate(
                10L,
                100L
        )).thenReturn(Optional.of(ownerMember));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.ban(
                        2L,
                        100L,
                        new OpenChatBanCreateRequestDto(10L, "reason")
                ))
                .satisfies(exception -> assertThat(
                        exception.getErrorCode()
                ).isEqualTo(OpenChatErrorCode.BAN_ROLE_FORBIDDEN));

        verify(banRepository, never()).save(any());
    }

    @Test
    void rejectsSelfBan() {
        when(memberRepository.findActiveByRoomIdAndUserIdForUpdate(
                100L,
                1L
        )).thenReturn(Optional.of(ownerMember));
        when(memberRepository.findActiveByIdAndRoomIdForUpdate(
                10L,
                100L
        )).thenReturn(Optional.of(ownerMember));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.ban(
                        1L,
                        100L,
                        new OpenChatBanCreateRequestDto(10L, "reason")
                ))
                .satisfies(exception -> assertThat(
                        exception.getErrorCode()
                ).isEqualTo(OpenChatErrorCode.BAN_SELF_NOT_ALLOWED));
    }

    @Test
    void ownerCanReleaseEveryBanButAdminCannotReleaseOwnerBan() {
        OpenChatBan ownerBan = ban(
                ownerMember,
                ChatRoomMemberRole.OWNER,
                ChatRoomMemberRole.MEMBER
        );
        ReflectionTestUtils.setField(ownerBan, "id", 71L);

        when(memberRepository.findActiveByRoomIdAndUserIdForUpdate(
                100L,
                2L
        )).thenReturn(Optional.of(adminMember));
        when(banRepository.findActiveByIdAndRoomIdForUpdate(
                71L,
                100L
        )).thenReturn(Optional.of(ownerBan));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.release(
                        2L,
                        100L,
                        71L
                ))
                .satisfies(exception -> assertThat(
                        exception.getErrorCode()
                ).isEqualTo(OpenChatErrorCode.BAN_RELEASE_FORBIDDEN));

        when(memberRepository.findActiveByRoomIdAndUserIdForUpdate(
                100L,
                1L
        )).thenReturn(Optional.of(ownerMember));

        service.release(1L, 100L, 71L);
        assertThat(ownerBan.isActive()).isFalse();
        assertThat(ownerBan.getReleasedByMember())
                .isSameAs(ownerMember);
    }

    @Test
    void adminCanReleaseAdminHierarchyMemberBan() {
        targetMember.banFromOpenChat();
        OpenChatBan adminBan = ban(
                adminMember,
                ChatRoomMemberRole.ADMIN,
                ChatRoomMemberRole.MEMBER
        );
        ReflectionTestUtils.setField(adminBan, "id", 72L);

        when(memberRepository.findActiveByRoomIdAndUserIdForUpdate(
                100L,
                2L
        )).thenReturn(Optional.of(adminMember));
        when(banRepository.findActiveByIdAndRoomIdForUpdate(
                72L,
                100L
        )).thenReturn(Optional.of(adminBan));

        service.release(2L, 100L, 72L);

        assertThat(adminBan.isActive()).isFalse();
        assertThat(targetMember.isActive()).isFalse();
    }

    private OpenChatBan ban(
            ChatRoomMember actor,
            ChatRoomMemberRole actorRole,
            ChatRoomMemberRole targetRole
    ) {
        return OpenChatBan.create(
                chatRoom,
                targetMember.getUser(),
                targetMember,
                targetProfile.getMemberCode(),
                targetProfile.getNickname(),
                targetProfile.getProfileImageObjectKey(),
                targetMember.getJoinedAt(),
                targetRole,
                actor,
                actorRole,
                "reason"
        );
    }

    private ChatRoomMember member(
            Long id,
            User user,
            ChatRoomMemberRole role
    ) {
        ChatRoomMember member = role == ChatRoomMemberRole.OWNER
                ? ChatRoomMember.createOwner(
                        chatRoom,
                        user,
                        "ko",
                        "ja"
                )
                : ChatRoomMember.createMember(
                        chatRoom,
                        user,
                        "ko",
                        "ja"
                );
        member.changeRole(role);
        ReflectionTestUtils.setField(member, "id", id);
        member.initializeReadCursor(99L);
        return member;
    }

    private User user(Long id, String email, String publicId) {
        User user = User.createLocalUser(
                email,
                "password",
                email,
                Role.USER,
                publicId
        );
        user.setId(id);
        return user;
    }
}
