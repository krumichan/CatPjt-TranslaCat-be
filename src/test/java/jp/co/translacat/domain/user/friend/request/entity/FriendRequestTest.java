package jp.co.translacat.domain.user.friend.request.entity;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.friend.request.enums.FriendRequestStatus;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class FriendRequestTest {

    @Test
    @DisplayName("FriendRequest 생성 테스트")
    void createFriendRequest() {
        // given
        User requester = createUser(1L, "requester@example.com", "requester", "TCAT-00000001");
        User receiver = createUser(2L, "receiver@example.com", "receiver", "TCAT-00000002");

        // when
        FriendRequest friendRequest = FriendRequest.create(requester, receiver);

        // then
        assertThat(friendRequest.getRequesterUser()).isEqualTo(requester);
        assertThat(friendRequest.getReceiverUser()).isEqualTo(receiver);
        assertThat(friendRequest.getStatus()).isEqualTo(FriendRequestStatus.PENDING);
        assertThat(friendRequest.getRequestedAt()).isNotNull();
        assertThat(friendRequest.getRespondedAt()).isNull();
        assertThat(friendRequest.isPending()).isTrue();
    }

    @Test
    @DisplayName("자기 자신에게 친구 요청할 수 없다")
    void failWhenRequestToSelf() {
        // given
        User requester = createUser(1L, "requester@example.com", "requester", "TCAT-00000001");
        User receiver = createUser(1L, "requester@example.com", "requester", "TCAT-00000001");

        // when
        BusinessException exception = catchThrowableOfType(
                () -> FriendRequest.create(requester, receiver),
                BusinessException.class
        );

        // then
        assertThat(exception).isNotNull();
        assertThat(exception.getErrorCode()).isEqualTo("FRIEND_REQUEST_SELF_NOT_ALLOWED");
    }

    @Test
    @DisplayName("친구 요청 수락 상태 전이 테스트")
    void acceptFriendRequest() {
        // given
        FriendRequest friendRequest = createFriendRequest();

        // when
        friendRequest.accept();

        // then
        assertThat(friendRequest.getStatus()).isEqualTo(FriendRequestStatus.ACCEPTED);
        assertThat(friendRequest.getRespondedAt()).isNotNull();
        assertThat(friendRequest.isPending()).isFalse();
    }

    @Test
    @DisplayName("친구 요청 거절 상태 전이 테스트")
    void rejectFriendRequest() {
        // given
        FriendRequest friendRequest = createFriendRequest();

        // when
        friendRequest.reject();

        // then
        assertThat(friendRequest.getStatus()).isEqualTo(FriendRequestStatus.REJECTED);
        assertThat(friendRequest.getRespondedAt()).isNotNull();
        assertThat(friendRequest.isPending()).isFalse();
    }

    @Test
    @DisplayName("친구 요청 취소 상태 전이 테스트")
    void cancelFriendRequest() {
        // given
        FriendRequest friendRequest = createFriendRequest();

        // when
        friendRequest.cancel();

        // then
        assertThat(friendRequest.getStatus()).isEqualTo(FriendRequestStatus.CANCELED);
        assertThat(friendRequest.getRespondedAt()).isNotNull();
        assertThat(friendRequest.isPending()).isFalse();
    }

    @Test
    @DisplayName("이미 처리된 요청은 재처리할 수 없다")
    void failWhenAlreadyProcessedRequest() {
        // given
        FriendRequest friendRequest = createFriendRequest();
        friendRequest.accept();

        // when
        BusinessException exception = catchThrowableOfType(
                friendRequest::reject,
                BusinessException.class
        );

        // then
        assertThat(exception).isNotNull();
        assertThat(exception.getErrorCode()).isEqualTo("FRIEND_REQUEST_ALREADY_PROCESSED");
    }

    private FriendRequest createFriendRequest() {
        User requester = createUser(1L, "requester@example.com", "requester", "TCAT-00000001");
        User receiver = createUser(2L, "receiver@example.com", "receiver", "TCAT-00000002");
        return FriendRequest.create(requester, receiver);
    }

    private User createUser(
            Long id,
            String email,
            String username,
            String publicId
    ) {
        User user = User.createLocalUser(
                email,
                "password",
                username,
                Role.USER,
                publicId
        );
        user.setId(id);
        return user;
    }
}
