package jp.co.translacat.domain.user.block.entity;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class UserBlockTest {

    @Test
    @DisplayName("사용자 차단 관계 생성 테스트")
    void createUserBlock() {
        // given
        User blocker = createUser(1L, "blocker@example.com", "blocker", "TCAT-00000001");
        User blocked = createUser(2L, "blocked@example.com", "blocked", "TCAT-00000002");

        // when
        UserBlock userBlock = UserBlock.create(blocker, blocked);

        // then
        assertThat(userBlock.getBlockerUser()).isEqualTo(blocker);
        assertThat(userBlock.getBlockedUser()).isEqualTo(blocked);
        assertThat(userBlock.isActive()).isTrue();
        assertThat(userBlock.isDeleted()).isFalse();
        assertThat(userBlock.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("자기 자신을 차단할 수 없다")
    void failWhenBlockSelf() {
        // given
        User user = createUser(1L, "user@example.com", "user", "TCAT-00000001");

        // when & then
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> UserBlock.create(user, user))
                .satisfies(exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("USER_BLOCK_SELF_NOT_ALLOWED")
                );
    }

    @Test
    @DisplayName("차단 방향 확인 테스트")
    void checkBlockDirection() {
        // given
        User blocker = createUser(1L, "blocker@example.com", "blocker", "TCAT-00000001");
        User blocked = createUser(2L, "blocked@example.com", "blocked", "TCAT-00000002");
        UserBlock userBlock = UserBlock.create(blocker, blocked);

        // when & then
        assertThat(userBlock.isBlockerAndBlocked(1L, 2L)).isTrue();
        assertThat(userBlock.isBlockerAndBlocked(2L, 1L)).isFalse();
        assertThat(userBlock.isBlockedBetween(1L, 2L)).isTrue();
        assertThat(userBlock.isBlockedBetween(2L, 1L)).isTrue();
        assertThat(userBlock.isBlockedBy(1L)).isTrue();
        assertThat(userBlock.isBlockedBy(2L)).isFalse();
    }

    @Test
    @DisplayName("차단 관계 soft delete 테스트")
    void softDeleteUserBlock() {
        // given
        UserBlock userBlock = createPendingUserBlock();

        // when
        userBlock.softDelete();

        // then
        assertThat(userBlock.isDeleted()).isTrue();
        assertThat(userBlock.isActive()).isFalse();
        assertThat(userBlock.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("soft delete된 차단 관계 복구 테스트")
    void restoreUserBlock() {
        // given
        UserBlock userBlock = createPendingUserBlock();
        userBlock.softDelete();

        // when
        userBlock.restore();

        // then
        assertThat(userBlock.isDeleted()).isFalse();
        assertThat(userBlock.isActive()).isTrue();
        assertThat(userBlock.getDeletedAt()).isNull();
    }

    private UserBlock createPendingUserBlock() {
        User blocker = createUser(1L, "blocker@example.com", "blocker", "TCAT-00000001");
        User blocked = createUser(2L, "blocked@example.com", "blocked", "TCAT-00000002");
        return UserBlock.create(blocker, blocked);
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
