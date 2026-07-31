package jp.co.translacat.domain.chat.openchat.service;

import jp.co.translacat.domain.chat.language.dto.ChatLanguageSettingResult;
import jp.co.translacat.domain.chat.language.service.UserChatLanguageSettingService;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.openchat.dto.request.OpenChatOwnerProfileCreateRequestDto;
import jp.co.translacat.domain.chat.openchat.dto.request.OpenChatRoomCreateRequestDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatRoomDetailResponseDto;
import jp.co.translacat.domain.chat.openchat.entity.OpenChatRoom;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatJoinBlockedReason;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatRoomStatus;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatVisibility;
import jp.co.translacat.domain.chat.openchat.profile.entity.OpenChatMemberProfile;
import jp.co.translacat.domain.chat.openchat.profile.repository.OpenChatMemberProfileRepository;
import jp.co.translacat.domain.chat.openchat.repository.OpenChatRoomRepository;
import jp.co.translacat.domain.chat.openchat.support.OpenChatErrorCode;
import jp.co.translacat.domain.chat.openchat.support.OpenChatMemberCodeGenerator;
import jp.co.translacat.domain.chat.openchat.support.OpenChatPolicy;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomSourceType;
import jp.co.translacat.domain.chat.room.enums.ChatRoomType;
import jp.co.translacat.domain.chat.room.repository.ChatRoomRepository;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.service.UserService;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenChatRoomCommandServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private OpenChatRoomRepository openChatRoomRepository;

    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Mock
    private OpenChatMemberProfileRepository profileRepository;

    @Mock
    private UserService userService;

    @Mock
    private UserChatLanguageSettingService languageSettingService;

    @Mock
    private OpenChatMemberCodeGenerator memberCodeGenerator;

    @Mock
    private OpenChatRoomQueryService queryService;

    @InjectMocks
    private OpenChatRoomCommandService commandService;

    @Test
    @DisplayName("OPEN 방·OWNER 멤버·방별 프로필을 생성한다")
    void createOpenRoom() {
        Long userId = 1L;
        User owner = createUser(userId);
        OpenChatRoomCreateRequestDto request =
                new OpenChatRoomCreateRequestDto(
                        "  일본어 회화  ",
                        "  일본어로 대화합니다.  ",
                        OpenChatVisibility.PUBLIC,
                        null,
                        new OpenChatOwnerProfileCreateRequestDto(
                                "  日本語初心者  ",
                                null
                        )
                );
        OpenChatRoomDetailResponseDto expected =
                expectedResponse();

        when(userService.getById(userId)).thenReturn(owner);
        when(languageSettingService.resolveDefault(userId))
                .thenReturn(new ChatLanguageSettingResult(
                        "ko",
                        "ja",
                        false
                ));
        when(chatRoomRepository.save(any(ChatRoom.class)))
                .thenAnswer(invocation -> {
                    ChatRoom room = invocation.getArgument(0);
                    ReflectionTestUtils.setField(room, "id", 100L);
                    return room;
                });
        when(openChatRoomRepository.save(any(OpenChatRoom.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(chatRoomMemberRepository.save(any(ChatRoomMember.class)))
                .thenAnswer(invocation -> {
                    ChatRoomMember member = invocation.getArgument(0);
                    ReflectionTestUtils.setField(member, "id", 481L);
                    return member;
                });
        when(memberCodeGenerator.generate()).thenReturn("OC-A7K2M");
        when(profileRepository.save(any(OpenChatMemberProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(queryService.getDetail(userId, 100L))
                .thenReturn(expected);

        OpenChatRoomDetailResponseDto actual =
                commandService.create(userId, request);

        assertThat(actual).isEqualTo(expected);

        ArgumentCaptor<ChatRoom> roomCaptor =
                ArgumentCaptor.forClass(ChatRoom.class);
        verify(chatRoomRepository).save(roomCaptor.capture());
        ChatRoom savedRoom = roomCaptor.getValue();
        assertThat(savedRoom.getRoomType())
                .isEqualTo(ChatRoomType.OPEN);
        assertThat(savedRoom.getSourceType())
                .isEqualTo(ChatRoomSourceType.OPEN);
        assertThat(savedRoom.getName()).isEqualTo("일본어 회화");
        assertThat(savedRoom.getDescription())
                .isEqualTo("일본어로 대화합니다.");

        ArgumentCaptor<OpenChatRoom> openRoomCaptor =
                ArgumentCaptor.forClass(OpenChatRoom.class);
        verify(openChatRoomRepository)
                .save(openRoomCaptor.capture());
        assertThat(openRoomCaptor.getValue().getMaxMemberCount())
                .isEqualTo(
                        OpenChatPolicy.DEFAULT_MAX_MEMBER_COUNT
                );
        assertThat(openRoomCaptor.getValue().getVisibility())
                .isEqualTo(OpenChatVisibility.PUBLIC);

        ArgumentCaptor<ChatRoomMember> memberCaptor =
                ArgumentCaptor.forClass(ChatRoomMember.class);
        verify(chatRoomMemberRepository)
                .save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getRole())
                .isEqualTo(ChatRoomMemberRole.OWNER);
        assertThat(memberCaptor.getValue().getUser()).isEqualTo(owner);

        ArgumentCaptor<OpenChatMemberProfile> profileCaptor =
                ArgumentCaptor.forClass(
                        OpenChatMemberProfile.class
                );
        verify(profileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getMemberCode())
                .isEqualTo("OC-A7K2M");
        assertThat(profileCaptor.getValue().getNickname())
                .isEqualTo("日本語初心者");

        verify(chatRoomRepository).flush();
        verify(queryService).getDetail(userId, 100L);
    }

    @Test
    @DisplayName("최대 인원이 범위를 벗어나면 생성하지 않는다")
    void rejectInvalidMaxMemberCount() {
        OpenChatRoomCreateRequestDto request =
                new OpenChatRoomCreateRequestDto(
                        "공개방",
                        "설명",
                        OpenChatVisibility.PUBLIC,
                        101,
                        new OpenChatOwnerProfileCreateRequestDto(
                                "고양이",
                                null
                        )
                );

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> commandService.create(1L, request))
                .satisfies(exception -> assertThat(
                        exception.getErrorCode()
                ).isEqualTo(
                        OpenChatErrorCode
                                .MAX_MEMBER_COUNT_INVALID
                ));

        verifyNoInteractions(
                chatRoomRepository,
                openChatRoomRepository,
                chatRoomMemberRepository,
                profileRepository,
                userService
        );
    }

    @Test
    @DisplayName("OPEN 프로필 Prefix가 아닌 Object Key는 거부한다")
    void rejectInvalidObjectKeyPrefix() {
        OpenChatRoomCreateRequestDto request =
                new OpenChatRoomCreateRequestDto(
                        "공개방",
                        "설명",
                        OpenChatVisibility.PUBLIC,
                        50,
                        new OpenChatOwnerProfileCreateRequestDto(
                                "고양이",
                                "profile/1/avatar.png"
                        )
                );

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> commandService.create(1L, request))
                .satisfies(exception -> assertThat(
                        exception.getErrorCode()
                ).isEqualTo(
                        OpenChatErrorCode
                                .PROFILE_IMAGE_OBJECT_KEY_INVALID
                ));

        verifyNoInteractions(userService, chatRoomRepository);
    }

    private OpenChatRoomDetailResponseDto expectedResponse() {
        return new OpenChatRoomDetailResponseDto(
                100L,
                ChatRoomType.OPEN,
                ChatRoomSourceType.OPEN,
                "일본어 회화",
                "일본어로 대화합니다.",
                OpenChatVisibility.PUBLIC,
                OpenChatRoomStatus.ACTIVE,
                1L,
                50,
                true,
                false,
                OpenChatJoinBlockedReason.ALREADY_JOINED,
                ChatRoomMemberRole.OWNER,
                null,
                null,
                null,
                null,
                null
        );
    }

    private User createUser(Long id) {
        User user = User.createLocalUser(
                "owner-command@example.com",
                "password",
                "owner",
                Role.USER,
                "OPENCMD0001"
        );
        user.setId(id);
        return user;
    }
}
