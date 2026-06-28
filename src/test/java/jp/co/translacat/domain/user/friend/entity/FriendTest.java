package jp.co.translacat.domain.user.friend.entity;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class FriendTest {

    @Test
    @DisplayName("친구 관계 생성 테스트")
    void createFriend() {
        // given
        User user1 = createUser(1L, "user1@example.com", "user1", "TCAT-00000001");
        User user2 = createUser(2L, "user2@example.com", "user2", "TCAT-00000002");

        // when
        Friend friend = Friend.create(user1, user2);

        // then
        assertThat(friend.getUserLow()).isEqualTo(user1);
        assertThat(friend.getUserHigh()).isEqualTo(user2);
        assertThat(friend.isActive()).isTrue();
        assertThat(friend.isDeleted()).isFalse();
        assertThat(friend.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("친구 관계는 userId 순서와 상관없이 정규화된다")
    void normalizeFriendUserOrder() {
        // given
        User user1 = createUser(1L, "user1@example.com", "user1", "TCAT-00000001");
        User user2 = createUser(2L, "user2@example.com", "user2", "TCAT-00000002");

        // when
        Friend friend = Friend.create(user2, user1);

        // then
        assertThat(friend.getUserLow()).isEqualTo(user1);
        assertThat(friend.getUserHigh()).isEqualTo(user2);
    }

    @Test
    @DisplayName("자기 자신과 친구 관계를 생성할 수 없다")
    void failWhenCreateFriendWithSelf() {
        // given
        User user = createUser(1L, "user@example.com", "user", "TCAT-00000001");

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> Friend.create(user, user))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("FRIEND_SELF_NOT_ALLOWED")
                );
    }

    @Test
    @DisplayName("친구 관계 포함 사용자 확인 테스트")
    void containsUser() {
        // given
        User user1 = createUser(1L, "user1@example.com", "user1", "TCAT-00000001");
        User user2 = createUser(2L, "user2@example.com", "user2", "TCAT-00000002");
        Friend friend = Friend.create(user1, user2);

        // when & then
        assertThat(friend.containsUser(1L)).isTrue();
        assertThat(friend.containsUser(2L)).isTrue();
        assertThat(friend.containsUser(3L)).isFalse();
        assertThat(friend.isBetween(1L, 2L)).isTrue();
        assertThat(friend.isBetween(2L, 1L)).isTrue();
    }

    @Test
    @DisplayName("친구 관계 soft delete 테스트")
    void softDeleteFriend() {
        // given
        Friend friend = createPendingFriend();

        // when
        friend.softDelete();

        // then
        assertThat(friend.isDeleted()).isTrue();
        assertThat(friend.isActive()).isFalse();
        assertThat(friend.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("soft delete된 친구 관계 복구 테스트")
    void restoreFriend() {
        // given
        Friend friend = createPendingFriend();
        friend.softDelete();

        // when
        friend.restore();

        // then
        assertThat(friend.isDeleted()).isFalse();
        assertThat(friend.isActive()).isTrue();
        assertThat(friend.getDeletedAt()).isNull();
    }

    private Friend createPendingFriend() {
        User user1 = createUser(1L, "user1@example.com", "user1", "TCAT-00000001");
        User user2 = createUser(2L, "user2@example.com", "user2", "TCAT-00000002");
        return Friend.create(user1, user2);
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
