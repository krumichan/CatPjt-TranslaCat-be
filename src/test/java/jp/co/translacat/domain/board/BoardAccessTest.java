package jp.co.translacat.domain.board;

import jp.co.translacat.domain.user.auth.AuthAttemptLimiter;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.global.security.JWTService;
import jp.co.translacat.global.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.UUID;

import static jp.co.translacat.domain.board.BoardDtos.Board;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BoardAccessTest {

    private final JWTService jwt = mock(JWTService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final BoardAccess access = new BoardAccess(jwt, jdbc, encoder, new AuthAttemptLimiter());

    @Test
    void adminBoardRequiresCurrentAdminRole() {
        // 준비
        User user = mock(User.class);
        when(user.isActiveAccount()).thenReturn(true);
        when(user.getAuthority()).thenReturn(Role.USER);

        // 실행 및 검증
        assertThatThrownBy(() -> access.reader(new UserPrincipal(user), "Bearer a", Board.ADMIN))
                .isInstanceOf(BoardException.class).hasMessage("BOARD_NOT_FOUND");
        verifyNoInteractions(jwt);
    }

    @Test
    void grantIsScopedToEntryViewerAndSession() {
        // 준비
        String password = "open me 2026";
        UUID id = UUID.randomUUID();
        String hash = access.passwordHash(password);
        var row = new BoardRepository.EntryRow(id, null, null, 7, null, "title", "body",
                hash, 1, Instant.EPOCH, Instant.EPOCH);
        var correctSession = new BoardAccess.Reader(7, "first");
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(id.toString()), eq(7L), eq("first"), eq(1)))
                .thenReturn(true);

        // 실행 및 검증
        assertThat(hash).doesNotContain(password);
        assertThatThrownBy(() -> access.unlock(correctSession, row, "wrong password"))
                .isInstanceOf(BoardException.class).hasMessage("BOARD_WRONG_PASSWORD");
        access.unlock(correctSession, row, password);
        verify(jdbc).update(anyString(), eq(id.toString()), eq(7L), eq("first"), eq(1), eq(1));
        assertThat(access.unlocked(correctSession, row)).isTrue();
        assertThat(access.unlocked(new BoardAccess.Reader(7, "second"), row)).isFalse();
        assertThat(access.unlocked(new BoardAccess.Reader(8, "first"), row)).isFalse();

        // 비밀번호가 바뀐 버전에는 이전 세션의 잠금 권한을 재사용하지 않는다.
        var edited = new BoardRepository.EntryRow(id, null, null, 7, null, "title", "body",
                hash, 2, Instant.EPOCH, Instant.EPOCH);
        assertThat(access.unlocked(correctSession, edited)).isFalse();
    }

    @Test
    void rejectsPasswordsThatWouldExceedBcryptByteLimit() {
        assertThatThrownBy(() -> access.passwordHash("비".repeat(25)))
                .isInstanceOf(BoardException.class).hasMessage("BOARD_INVALID_INPUT");
    }
}
