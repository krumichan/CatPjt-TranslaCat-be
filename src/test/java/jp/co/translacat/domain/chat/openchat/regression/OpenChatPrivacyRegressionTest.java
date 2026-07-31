package jp.co.translacat.domain.chat.openchat.regression;

import jp.co.translacat.domain.chat.language.dto.ChatLanguageSettingResult;
import jp.co.translacat.domain.chat.member.entity.ChatRoomMember;
import jp.co.translacat.domain.chat.member.repository.ChatRoomMemberRepository;
import jp.co.translacat.domain.chat.member.service.ChatRoomMemberQueryService;
import jp.co.translacat.domain.chat.message.dto.response.ChatMessageResponseDto;
import jp.co.translacat.domain.chat.message.entity.ChatMessage;
import jp.co.translacat.domain.chat.room.dto.response.ChatRoomListItemResponseDto;
import jp.co.translacat.domain.chat.room.dto.response.ChatRoomResponseDto;
import jp.co.translacat.domain.chat.room.entity.ChatRoom;
import jp.co.translacat.domain.user.block.service.UserBlockService;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.friend.request.repository.FriendRequestRepository;
import jp.co.translacat.domain.user.friend.service.FriendService;
import jp.co.translacat.domain.user.profile.service.UserProfileQueryService;
import jp.co.translacat.domain.chat.language.service.ChatLanguageSettingResolver;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenChatPrivacyRegressionTest {

    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Mock
    private ChatLanguageSettingResolver chatLanguageSettingResolver;

    @Mock
    private UserProfileQueryService userProfileQueryService;

    @Mock
    private FriendService friendService;

    @Mock
    private FriendRequestRepository friendRequestRepository;

    @Mock
    private UserBlockService userBlockService;

    @InjectMocks
    private ChatRoomMemberQueryService memberQueryService;

    @Test
    @DisplayName("기존 채팅방 응답에서 OPEN 방 ownerId를 노출하지 않는다")
    void hideOwnerIdFromGeneralRoomResponses() {
        User owner = createUser(1L);
        ChatRoom room = ChatRoom.createOpenRoom(
                "공개방",
                "설명",
                owner
        );
        ChatLanguageSettingResult languageSetting =
                new ChatLanguageSettingResult(
                        "ko",
                        "ja",
                        false
                );

        ChatRoomResponseDto detail = ChatRoomResponseDto.from(
                room,
                languageSetting,
                1L
        );
        ChatRoomListItemResponseDto listItem =
                ChatRoomListItemResponseDto.from(room, 1L);

        assertThat(detail.ownerId()).isNull();
        assertThat(listItem.ownerId()).isNull();
    }

    @Test
    @DisplayName("기존 메시지 응답에서 OPEN 방 일반 사용자 식별 정보를 숨긴다")
    void hideGeneralSenderIdentityFromOpenMessage() {
        User sender = createUser(2L);
        ChatRoom room = ChatRoom.createOpenRoom(
                "공개방",
                "설명",
                sender
        );
        ChatMessage message = ChatMessage.createUserTextMessage(
                room,
                sender,
                "안녕하세요"
        );

        ChatMessageResponseDto response =
                ChatMessageResponseDto.from(
                        message,
                        "https://cdn.example/general-profile.png",
                        List.of(),
                        1L
                );

        assertThat(response.senderUserId()).isNull();
        assertThat(response.senderName()).isNull();
        assertThat(response.senderEmail()).isNull();
        assertThat(response.senderProfileImageUrl()).isNull();
    }

    @Test
    @DisplayName("OPEN 방에서 기존 일반 멤버 프로필 API 사용을 차단한다")
    void blockGeneralMemberProfileApiForOpenRoom() {
        User owner = createUser(1L);
        ChatRoom room = ChatRoom.createOpenRoom(
                "공개방",
                "설명",
                owner
        );
        ChatRoomMember member = ChatRoomMember.createOwner(
                room,
                owner,
                "ko",
                "ja"
        );

        when(chatRoomMemberRepository
                .findByChatRoomIdAndUserIdAndActiveTrueAndDeletedAtIsNull(
                        100L,
                        1L
                ))
                .thenReturn(Optional.of(member));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() ->
                        memberQueryService.getMembers(1L, 100L)
                )
                .satisfies(exception -> assertThat(
                        exception.getErrorCode()
                ).isEqualTo(
                        "OPEN_CHAT_MEMBER_PROFILE_API_REQUIRED"
                ));

        verifyNoInteractions(userProfileQueryService);
    }

    private User createUser(Long id) {
        User user = User.createLocalUser(
                "open-privacy-" + id + "@example.com",
                "password",
                "privacy-" + id,
                Role.USER,
                "OPENPRIV" + id
        );
        user.setId(id);
        return user;
    }
}
