package jp.co.translacat.domain.chat.openchat.service;

import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.openchat.entity.OpenChatRoom;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatVisibility;
import jp.co.translacat.domain.chat.openchat.repository.OpenChatRoomRepository;
import jp.co.translacat.domain.chat.openchat.support.OpenChatErrorCode;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.chat.room.enums.ChatRoomSourceType;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenChatAccessServiceTest {

    @Mock private ChatRoomMemberRepository memberRepository;
    @Mock private OpenChatRoomRepository openChatRoomRepository;

    private OpenChatAccessService service;
    private ChatRoom openRoomEntity;
    private ChatRoomMember openMember;
    private OpenChatRoom openChatRoom;

    @BeforeEach
    void setUp() {
        service = new OpenChatAccessService(
                memberRepository,
                openChatRoomRepository
        );

        User user = User.createLocalUser(
                "access@open.test",
                "password",
                "access-user",
                Role.USER,
                "OPENACCESS1"
        );
        user.setId(10L);
        openRoomEntity = ChatRoom.createOpenRoom("open", "desc", user);
        ReflectionTestUtils.setField(openRoomEntity, "id", 100L);
        openMember = ChatRoomMember.createOwner(
                openRoomEntity,
                user,
                "ko",
                "ja"
        );
        openChatRoom = OpenChatRoom.create(
                openRoomEntity,
                OpenChatVisibility.PUBLIC,
                50
        );
    }

    @Test
    void rejectsMessageWhenOpenRoomIsClosed() {
        openChatRoom.close();
        when(openChatRoomRepository.findByChatRoomId(100L))
                .thenReturn(Optional.of(openChatRoom));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.validateMessageSendAllowed(
                        openMember
                ))
                .satisfies(exception -> assertThat(
                        exception.getErrorCode()
                ).isEqualTo(OpenChatErrorCode.ROOM_CLOSED));
    }

    @Test
    void ignoresGeneralChatRoomForOpenSpecificValidation() {
        User user = openMember.getUser();
        ChatRoom groupRoom = ChatRoom.createGroupRoom(
                "group",
                null,
                user,
                ChatRoomSourceType.MANUAL
        );
        ChatRoomMember groupMember = ChatRoomMember.createMember(
                groupRoom,
                user,
                "ko",
                "ja"
        );

        service.validateMessageSendAllowed(groupMember);
    }

    @Test
    void returnsOnlyActiveOpenMember() {
        when(memberRepository
                .findByChatRoomIdAndUserIdAndActiveTrueAndDeletedAtIsNull(
                        100L,
                        10L
                ))
                .thenReturn(Optional.of(openMember));

        assertThat(service.getActiveOpenMember(10L, 100L))
                .isSameAs(openMember);
    }
}
