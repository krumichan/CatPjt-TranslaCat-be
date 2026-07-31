package jp.co.translacat.domain.chat.openchat.service;

import jp.co.translacat.domain.chat.language.dto.ChatLanguageSettingResult;
import jp.co.translacat.domain.chat.language.enums.ChatLanguageSettingSource;
import jp.co.translacat.domain.chat.language.service.UserChatLanguageSettingService;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.message.repository.ChatMessageRepository;
import jp.co.translacat.domain.chat.openchat.dto.request.OpenChatJoinRequestDto;
import jp.co.translacat.domain.chat.openchat.dto.request.OpenChatOwnerTransferRequestDto;
import jp.co.translacat.domain.chat.openchat.dto.request.OpenChatProfileRequestDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatMemberProfileResponseDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatRoomDetailResponseDto;
import jp.co.translacat.domain.chat.openchat.entity.OpenChatRoom;
import jp.co.translacat.domain.chat.openchat.event.OpenChatProfileUpdatedApplicationEvent;
import jp.co.translacat.domain.chat.openchat.event.OpenChatRoomClosedApplicationEvent;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatVisibility;
import jp.co.translacat.domain.chat.openchat.profile.entity.OpenChatMemberProfile;
import jp.co.translacat.domain.chat.openchat.profile.repository.OpenChatMemberProfileRepository;
import jp.co.translacat.domain.chat.openchat.profile.service.OpenChatProfileResponseMapper;
import jp.co.translacat.domain.chat.openchat.repository.OpenChatRoomRepository;
import jp.co.translacat.domain.chat.openchat.support.OpenChatErrorCode;
import jp.co.translacat.domain.chat.openchat.support.OpenChatMemberCodeGenerator;
import jp.co.translacat.domain.chat.openchat.support.OpenChatProfileValidator;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.repository.ChatRoomRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.service.UserService;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenChatMembershipServiceTest {

    @Mock private OpenChatRoomRepository openChatRoomRepository;
    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private ChatRoomMemberRepository memberRepository;
    @Mock private OpenChatMemberProfileRepository profileRepository;
    @Mock private ChatMessageRepository messageRepository;
    @Mock private UserService userService;
    @Mock private UserChatLanguageSettingService languageSettingService;
    @Mock private OpenChatMemberCodeGenerator memberCodeGenerator;
    @Mock private OpenChatProfileValidator profileValidator;
    @Mock private OpenChatProfileResponseMapper profileResponseMapper;
    @Mock private OpenChatRoomQueryService roomQueryService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private OpenChatMembershipService service;
    private User owner;
    private User joiningUser;
    private ChatRoom chatRoom;
    private OpenChatRoom openChatRoom;

    @BeforeEach
    void setUp() {
        service = new OpenChatMembershipService(
                openChatRoomRepository,
                chatRoomRepository,
                memberRepository,
                profileRepository,
                messageRepository,
                userService,
                languageSettingService,
                memberCodeGenerator,
                profileValidator,
                profileResponseMapper,
                roomQueryService,
                eventPublisher
        );

        owner = user(1L, "owner@open.test", "OPENOWNER1");
        joiningUser = user(2L, "joiner@open.test", "OPENJOIN01");
        chatRoom = ChatRoom.createOpenRoom("open", "desc", owner);
        ReflectionTestUtils.setField(chatRoom, "id", 100L);
        openChatRoom = OpenChatRoom.create(
                chatRoom,
                OpenChatVisibility.PUBLIC,
                2
        );
        when(openChatRoomRepository.findByChatRoomIdForUpdate(100L))
                .thenReturn(Optional.of(openChatRoom));
    }

    @Test
    void firstJoinCreatesMemberProfileAndInitializesReadCursor() {
        OpenChatRoomDetailResponseDto expected = mock(
                OpenChatRoomDetailResponseDto.class
        );
        ChatMessage latestMessage = ChatMessage.createSystemMessage(
                chatRoom,
                "latest"
        );
        ReflectionTestUtils.setField(latestMessage, "id", 90L);

        when(memberRepository.findByChatRoomIdAndUserId(100L, 2L))
                .thenReturn(Optional.empty());
        when(memberRepository
                .countByChatRoomIdAndActiveTrueAndDeletedAtIsNull(100L))
                .thenReturn(1L);
        when(messageRepository
                .findTopByChatRoomIdAndStatusAndDeletedAtIsNullOrderByIdDesc(
                        eq(100L),
                        any()
                ))
                .thenReturn(Optional.of(latestMessage));
        when(languageSettingService.resolveDefault(2L))
                .thenReturn(languageSetting());
        when(userService.getById(2L)).thenReturn(joiningUser);
        when(memberRepository.save(any(ChatRoomMember.class)))
                .thenAnswer(invocation -> {
                    ChatRoomMember member = invocation.getArgument(0);
                    ReflectionTestUtils.setField(member, "id", 20L);
                    return member;
                });
        when(profileValidator.normalizeNickname("cat"))
                .thenReturn("cat");
        when(profileValidator.normalizeObjectKey(null, 20L))
                .thenReturn(null);
        when(memberCodeGenerator.generate()).thenReturn("OC-ABCDE");
        when(profileRepository.save(any(OpenChatMemberProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(roomQueryService.getDetail(2L, 100L))
                .thenReturn(expected);

        OpenChatRoomDetailResponseDto actual = service.join(
                2L,
                100L,
                new OpenChatJoinRequestDto(
                        new OpenChatProfileRequestDto("cat", null)
                )
        );

        assertThat(actual).isSameAs(expected);

        ArgumentCaptor<ChatRoomMember> memberCaptor =
                ArgumentCaptor.forClass(ChatRoomMember.class);
        verify(memberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getRole())
                .isEqualTo(ChatRoomMemberRole.MEMBER);
        assertThat(memberCaptor.getValue().getLastReadMessageId())
                .isEqualTo(90L);

        ArgumentCaptor<OpenChatMemberProfile> profileCaptor =
                ArgumentCaptor.forClass(OpenChatMemberProfile.class);
        verify(profileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getMemberCode())
                .isEqualTo("OC-ABCDE");
        assertThat(profileCaptor.getValue().getNickname())
                .isEqualTo("cat");
    }

    @Test
    void activeDuplicateJoinIsIdempotent() {
        ChatRoomMember activeMember = ChatRoomMember.createMember(
                chatRoom,
                joiningUser,
                "ko",
                "ja"
        );
        OpenChatRoomDetailResponseDto expected = mock(
                OpenChatRoomDetailResponseDto.class
        );

        when(memberRepository.findByChatRoomIdAndUserId(100L, 2L))
                .thenReturn(Optional.of(activeMember));
        when(roomQueryService.getDetail(2L, 100L))
                .thenReturn(expected);

        assertThat(service.join(2L, 100L, null))
                .isSameAs(expected);

        verify(memberRepository, never())
                .countByChatRoomIdAndActiveTrueAndDeletedAtIsNull(anyLong());
        verify(memberRepository, never()).save(any());
        verify(profileRepository, never()).save(any());
    }

    @Test
    void rejectsDuplicateJoinWhenRoomIsClosed() {
        ChatRoomMember activeMember = ChatRoomMember.createMember(
                chatRoom,
                joiningUser,
                "ko",
                "ja"
        );
        openChatRoom.close();

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.join(2L, 100L, null))
                .satisfies(exception -> assertThat(
                        exception.getErrorCode()
                ).isEqualTo(OpenChatErrorCode.ROOM_CLOSED));

        verify(memberRepository, never())
                .findByChatRoomIdAndUserId(100L, 2L);
    }

    @Test
    void rejectsJoinWhenCapacityIsFull() {
        when(memberRepository.findByChatRoomIdAndUserId(100L, 2L))
                .thenReturn(Optional.empty());
        when(memberRepository
                .countByChatRoomIdAndActiveTrueAndDeletedAtIsNull(100L))
                .thenReturn(2L);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.join(
                        2L,
                        100L,
                        new OpenChatJoinRequestDto(
                                new OpenChatProfileRequestDto("cat", null)
                        )
                ))
                .satisfies(exception -> assertThat(
                        exception.getErrorCode()
                ).isEqualTo(OpenChatErrorCode.ROOM_FULL));
    }

    @Test
    void rejoinReusesMemberAndMemberCode() {
        ChatRoomMember member = ChatRoomMember.createMember(
                chatRoom,
                joiningUser,
                "ko",
                "ja"
        );
        ReflectionTestUtils.setField(member, "id", 20L);
        member.leaveOpenChat();
        OpenChatMemberProfile profile = OpenChatMemberProfile.create(
                member,
                "OC-KEEP1",
                "old",
                "open-chat-profiles/20/old.png"
        );
        ChatMessage latest = ChatMessage.createSystemMessage(
                chatRoom,
                "latest"
        );
        ReflectionTestUtils.setField(latest, "id", 91L);
        OpenChatRoomDetailResponseDto expected = mock(
                OpenChatRoomDetailResponseDto.class
        );

        when(memberRepository.findByChatRoomIdAndUserId(100L, 2L))
                .thenReturn(Optional.of(member));
        when(memberRepository
                .countByChatRoomIdAndActiveTrueAndDeletedAtIsNull(100L))
                .thenReturn(1L);
        when(messageRepository
                .findTopByChatRoomIdAndStatusAndDeletedAtIsNullOrderByIdDesc(
                        eq(100L),
                        any()
                ))
                .thenReturn(Optional.of(latest));
        when(languageSettingService.resolveDefault(2L))
                .thenReturn(languageSetting());
        when(profileRepository.findByChatRoomMemberId(20L))
                .thenReturn(Optional.of(profile));
        when(roomQueryService.getDetail(2L, 100L))
                .thenReturn(expected);

        service.join(2L, 100L, null);

        assertThat(member.isActive()).isTrue();
        assertThat(member.getRole()).isEqualTo(ChatRoomMemberRole.MEMBER);
        assertThat(member.getLastReadMessageId()).isEqualTo(91L);
        assertThat(profile.getMemberCode()).isEqualTo("OC-KEEP1");
        assertThat(profile.getProfileImageObjectKey())
                .isEqualTo("open-chat-profiles/20/old.png");
        verify(profileRepository, never()).save(any());
    }

    @Test
    void ownerMustTransferBeforeLeaving() {
        ChatRoomMember ownerMember = ChatRoomMember.createOwner(
                chatRoom,
                owner,
                "ko",
                "ja"
        );
        when(memberRepository
                .findByChatRoomIdAndUserIdAndActiveTrueAndDeletedAtIsNull(
                        100L,
                        1L
                ))
                .thenReturn(Optional.of(ownerMember));
        when(memberRepository
                .countByChatRoomIdAndActiveTrueAndDeletedAtIsNull(100L))
                .thenReturn(2L);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.leave(1L, 100L))
                .satisfies(exception -> assertThat(
                        exception.getErrorCode()
                ).isEqualTo(
                        OpenChatErrorCode.OWNER_TRANSFER_REQUIRED
                ));
    }

    @Test
    void transferOwnerChangesBothRolesAndRoomOwner() {
        ChatRoomMember ownerMember = ChatRoomMember.createOwner(
                chatRoom,
                owner,
                "ko",
                "ja"
        );
        ReflectionTestUtils.setField(ownerMember, "id", 10L);
        ChatRoomMember targetMember = ChatRoomMember.createMember(
                chatRoom,
                joiningUser,
                "ko",
                "ja"
        );
        ReflectionTestUtils.setField(targetMember, "id", 20L);
        OpenChatRoomDetailResponseDto expected = mock(
                OpenChatRoomDetailResponseDto.class
        );

        when(memberRepository
                .findByChatRoomIdAndUserIdAndActiveTrueAndDeletedAtIsNull(
                        100L,
                        1L
                ))
                .thenReturn(Optional.of(ownerMember));
        when(memberRepository
                .findByIdAndChatRoomIdAndActiveTrueAndDeletedAtIsNull(
                        20L,
                        100L
                ))
                .thenReturn(Optional.of(targetMember));
        when(roomQueryService.getDetail(1L, 100L))
                .thenReturn(expected);

        service.transferOwner(
                1L,
                100L,
                new OpenChatOwnerTransferRequestDto(20L)
        );

        assertThat(ownerMember.getRole())
                .isEqualTo(ChatRoomMemberRole.MEMBER);
        assertThat(targetMember.getRole())
                .isEqualTo(ChatRoomMemberRole.OWNER);
        assertThat(chatRoom.getOwner()).isEqualTo(joiningUser);
    }

    @Test
    void closePublishesApplicationEvent() {
        ChatRoomMember ownerMember = ChatRoomMember.createOwner(
                chatRoom,
                owner,
                "ko",
                "ja"
        );
        when(memberRepository
                .findByChatRoomIdAndUserIdAndActiveTrueAndDeletedAtIsNull(
                        100L,
                        1L
                ))
                .thenReturn(Optional.of(ownerMember));
        when(roomQueryService.getDetail(1L, 100L))
                .thenReturn(mock(OpenChatRoomDetailResponseDto.class));

        service.close(1L, 100L);

        assertThat(openChatRoom.isClosed()).isTrue();
        verify(eventPublisher).publishEvent(
                any(OpenChatRoomClosedApplicationEvent.class)
        );
    }

    private ChatLanguageSettingResult languageSetting() {
        return new ChatLanguageSettingResult(
                "ko",
                "ja",
                true,
                true,
                false,
                ChatLanguageSettingSource.SYSTEM
        );
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
