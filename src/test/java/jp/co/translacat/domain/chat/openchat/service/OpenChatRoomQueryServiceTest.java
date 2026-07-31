package jp.co.translacat.domain.chat.openchat.service;

import jp.co.translacat.domain.chat.member.enums.ChatRoomMemberRole;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatRoomDetailResponseDto;
import jp.co.translacat.domain.chat.openchat.dto.response.OpenChatRoomListResponseDto;
import jp.co.translacat.domain.chat.openchat.entity.OpenChatRoom;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatJoinBlockedReason;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatRoomStatus;
import jp.co.translacat.domain.chat.openchat.enums.OpenChatVisibility;
import jp.co.translacat.domain.chat.openchat.profile.service.OpenChatProfileImageUrlResolver;
import jp.co.translacat.domain.chat.openchat.repository.OpenChatMemberProfileQueryRow;
import jp.co.translacat.domain.chat.openchat.repository.OpenChatRoomRepository;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenChatRoomQueryServiceTest {

    @Mock
    private OpenChatRoomRepository openChatRoomRepository;

    @Mock
    private OpenChatProfileImageUrlResolver imageUrlResolver;

    @InjectMocks
    private OpenChatRoomQueryService queryService;

    @Test
    @DisplayName("PUBLIC 목록을 Cursor로 조회하고 참여·정원 상태를 계산한다")
    void getPublicRooms() {
        OpenChatRoom joinedRoom = createRoom(
                300L,
                OpenChatVisibility.PUBLIC,
                20
        );
        OpenChatRoom fullRoom = createRoom(
                200L,
                OpenChatVisibility.PUBLIC,
                50
        );
        OpenChatRoom extraRoom = createRoom(
                100L,
                OpenChatVisibility.PUBLIC,
                50
        );
        List<Long> pageRoomIds = List.of(300L, 200L);
        LocalDateTime joinedActivity =
                LocalDateTime.of(2026, 7, 31, 18, 0);
        LocalDateTime fullActivity =
                LocalDateTime.of(2026, 7, 31, 17, 0);

        when(openChatRoomRepository.findPublicActivePage(
                "고양이",
                null,
                3
        )).thenReturn(List.of(
                joinedRoom,
                fullRoom,
                extraRoom
        ));
        when(openChatRoomRepository.countActiveMembers(pageRoomIds))
                .thenReturn(Map.of(
                        300L, 10L,
                        200L, 50L
                ));
        when(openChatRoomRepository.findJoinedRoomIds(1L, pageRoomIds))
                .thenReturn(Set.of(300L));
        when(openChatRoomRepository.findOwnerProfiles(pageRoomIds))
                .thenReturn(Map.of(
                        300L,
                        ownerRow(300L, 301L, "OC-AAAAA"),
                        200L,
                        ownerRow(200L, 201L, "OC-BBBBB")
                ));
        when(openChatRoomRepository.findLastActivityAt(pageRoomIds))
                .thenReturn(Map.of(
                        300L, joinedActivity,
                        200L, fullActivity
                ));
        when(imageUrlResolver.resolve(
                "open-chat-profiles/owner/avatar.png"
        )).thenReturn("https://cdn.example/avatar.png");

        OpenChatRoomListResponseDto response =
                queryService.getPublicRooms(
                        1L,
                        "  고양이  ",
                        null,
                        2
                );

        assertThat(response.openChatRooms()).hasSize(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursorId()).isEqualTo(200L);

        assertThat(response.openChatRooms().get(0).joined())
                .isTrue();
        assertThat(response.openChatRooms().get(0)
                .joinBlockedReason())
                .isEqualTo(
                        OpenChatJoinBlockedReason.ALREADY_JOINED
                );
        assertThat(response.openChatRooms().get(0)
                .lastActivityAt())
                .isEqualTo(joinedActivity);

        assertThat(response.openChatRooms().get(1).joinable())
                .isFalse();
        assertThat(response.openChatRooms().get(1)
                .joinBlockedReason())
                .isEqualTo(OpenChatJoinBlockedReason.ROOM_FULL);
        assertThat(response.openChatRooms().get(1)
                .ownerProfile().profileImageUrl())
                .isEqualTo("https://cdn.example/avatar.png");
    }

    @Test
    @DisplayName("UNLISTED 방 상세는 직접 조회할 수 있다")
    void getUnlistedDetail() {
        OpenChatRoom room = createRoom(
                400L,
                OpenChatVisibility.UNLISTED,
                50
        );
        List<Long> roomIds = List.of(400L);
        OpenChatMemberProfileQueryRow owner =
                ownerRow(400L, 401L, "OC-UNLST");

        when(openChatRoomRepository.findByChatRoomId(400L))
                .thenReturn(Optional.of(room));
        when(openChatRoomRepository.countActiveMembers(roomIds))
                .thenReturn(Map.of(400L, 2L));
        when(openChatRoomRepository.findJoinedRoomIds(1L, roomIds))
                .thenReturn(Set.of());
        when(openChatRoomRepository.findOwnerProfiles(roomIds))
                .thenReturn(Map.of(400L, owner));
        when(openChatRoomRepository.findLastActivityAt(roomIds))
                .thenReturn(Map.of());

        OpenChatRoomDetailResponseDto response =
                queryService.getDetail(1L, 400L);

        assertThat(response.visibility())
                .isEqualTo(OpenChatVisibility.UNLISTED);
        assertThat(response.joinable()).isTrue();
        assertThat(response.joinBlockedReason())
                .isEqualTo(OpenChatJoinBlockedReason.NONE);
        assertThat(response.joined()).isFalse();
        assertThat(response.myOpenProfile()).isNull();
        assertThat(response.ownerProfile().memberCode())
                .isEqualTo("OC-UNLST");
    }

    @Test
    @DisplayName("종료된 방 상세는 재참여 불가 상태를 반환한다")
    void closedRoomIsNotJoinable() {
        OpenChatRoom room = createRoom(
                500L,
                OpenChatVisibility.PUBLIC,
                50
        );
        room.close();
        List<Long> roomIds = List.of(500L);

        when(openChatRoomRepository.findByChatRoomId(500L))
                .thenReturn(Optional.of(room));
        when(openChatRoomRepository.countActiveMembers(roomIds))
                .thenReturn(Map.of(500L, 1L));
        when(openChatRoomRepository.findJoinedRoomIds(1L, roomIds))
                .thenReturn(Set.of());
        when(openChatRoomRepository.findOwnerProfiles(roomIds))
                .thenReturn(Map.of());
        when(openChatRoomRepository.findLastActivityAt(roomIds))
                .thenReturn(Map.of());

        OpenChatRoomDetailResponseDto response =
                queryService.getDetail(1L, 500L);

        assertThat(response.status())
                .isEqualTo(OpenChatRoomStatus.CLOSED);
        assertThat(response.joinable()).isFalse();
        assertThat(response.joinBlockedReason())
                .isEqualTo(
                        OpenChatJoinBlockedReason.ROOM_CLOSED
                );
    }

    private OpenChatRoom createRoom(
            Long roomId,
            OpenChatVisibility visibility,
            int maxMemberCount
    ) {
        User owner = User.createLocalUser(
                "owner-" + roomId + "@example.com",
                "password",
                "owner-" + roomId,
                Role.USER,
                "OPEN" + roomId
        );
        owner.setId(roomId + 1000);
        ChatRoom chatRoom = ChatRoom.createOpenRoom(
                "고양이 방 " + roomId,
                "고양이 설명 " + roomId,
                owner
        );
        ReflectionTestUtils.setField(chatRoom, "id", roomId);
        ReflectionTestUtils.setField(
                chatRoom,
                "createdAt",
                LocalDateTime.of(2026, 7, 31, 10, 0)
        );
        ReflectionTestUtils.setField(
                chatRoom,
                "updatedAt",
                LocalDateTime.of(2026, 7, 31, 11, 0)
        );
        return OpenChatRoom.create(
                chatRoom,
                visibility,
                maxMemberCount
        );
    }

    private OpenChatMemberProfileQueryRow ownerRow(
            Long roomId,
            Long memberId,
            String memberCode
    ) {
        return new OpenChatMemberProfileQueryRow(
                roomId,
                memberId,
                memberCode,
                "방장고양이",
                "open-chat-profiles/owner/avatar.png",
                ChatRoomMemberRole.OWNER,
                LocalDateTime.of(2026, 7, 31, 10, 0)
        );
    }
}
