package jp.co.translacat.domain.board;

import jp.co.translacat.domain.user.auth.AuthAttemptLimiter;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.global.security.JWTService;
import jp.co.translacat.global.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static jp.co.translacat.domain.board.BoardDtos.Board;

@Service
@RequiredArgsConstructor
class BoardAccess {

    private final JWTService jwt;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final AuthAttemptLimiter attempts;

    Reader reader(UserPrincipal principal, String authorization, Board board) {
        // 인증 필터가 확인한 현재 계정과 JWT의 로그인 세션을 사용한다.
        if (principal == null || !principal.user().isActiveAccount()
                || authorization == null || !authorization.startsWith("Bearer ")) {
            throw BoardException.missing();
        }

        User user = principal.user();
        if (board == Board.ADMIN && user.getAuthority() != Role.ADMIN) {
            throw BoardException.missing();
        }

        String token = authorization.substring("Bearer ".length());
        String session = jwt.getSessionId(token);

        // 이전 형식의 액세스 토큰은 토큰 지문에만 잠금 해제를 묶는다.
        return new Reader(user.getId(), hash(session == null ? "token:" + token : "session:" + session));
    }

    String passwordHash(String password) {
        if (password == null) {
            return null;
        }

        // BCrypt 입력을 바이트 단위로 검사해 다른 비밀번호가 같은 접두사로 잘리지 않게 한다.
        if (password.isBlank() || password.getBytes(StandardCharsets.UTF_8).length > 72
                || password.codePointCount(0, password.length()) < 8) {
            throw BoardException.invalid();
        }

        return passwords.encode(password);
    }

    boolean unlocked(Reader reader, BoardRepository.EntryRow entry) {
        if (entry.passwordHash() == null) {
            return true;
        }

        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM board_unlock
                    WHERE entry_id = ? AND viewer_id = ? AND session_hash = ?
                      AND revision = ? AND expires_at > UTC_TIMESTAMP(6)
                )
                """, Boolean.class, entry.id().toString(), reader.id(), reader.sessionHash(), entry.revision()));
    }

    void requireUnlocked(Reader reader, BoardRepository.EntryRow entry) {
        if (!unlocked(reader, entry)) {
            throw BoardException.locked();
        }
    }

    void unlock(Reader reader, BoardRepository.EntryRow entry, String password) {
        if (entry.passwordHash() == null) {
            return;
        }

        // 동일 계정의 반복 입력과 여러 계정에서 한 항목에 집중하는 비밀번호 대입을 제한한다.
        attempts.acquire("BOARD_ACCOUNT", Long.toString(reader.id()));
        attempts.acquire("BOARD_ENTRY", entry.id().toString());
        if (password.getBytes(StandardCharsets.UTF_8).length > 72
                || !passwords.matches(password, entry.passwordHash())) {
            throw new BoardException(HttpStatus.FORBIDDEN, "BOARD_WRONG_PASSWORD");
        }

        // 현재 항목 버전과 로그인 세션에만 잠금을 해제한다.
        jdbc.update("""
                INSERT INTO board_unlock (entry_id, viewer_id, session_hash, revision, expires_at)
                VALUES (?, ?, ?, ?, UTC_TIMESTAMP(6) + INTERVAL 30 MINUTE)
                ON DUPLICATE KEY UPDATE revision = ?, expires_at = UTC_TIMESTAMP(6) + INTERVAL 30 MINUTE
                """, entry.id().toString(), reader.id(), reader.sessionHash(), entry.revision(), entry.revision());
    }

    private static String hash(String identity) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    record Reader(long id, String sessionHash) {
    }
}
