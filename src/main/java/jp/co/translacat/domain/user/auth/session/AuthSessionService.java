package jp.co.translacat.domain.user.auth.session;

import jp.co.translacat.domain.user.auth.AuthException;
import jp.co.translacat.domain.user.dto.UserLoginResponseDto;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.LockModeType;
import jp.co.translacat.global.security.JWTService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthSessionService {
    private final AuthSessionRepository sessions;
    private final UserRepository users;
    private final JWTService jwt;

    @PersistenceContext
    private EntityManager entities;

    @Transactional
    public UserLoginResponseDto create(User user) {
        return createLocked(user, null);
    }

    @Transactional
    public UserLoginResponseDto createAfterPasswordAuthentication(User user, String authenticatedHash) {
        if (authenticatedHash == null || authenticatedHash.isBlank()) throw invalidRefresh();
        return createLocked(user, authenticatedHash);
    }

    private UserLoginResponseDto createLocked(User user, String authenticatedHash) {
        // 탈퇴와 새 발급이 같은 사용자 행을 잠가, 상태 변경 이후 세션이 되살아나지 않는다.
        User current = users.findLockedById(user.getId()).orElseThrow(this::invalidRefresh);
        entities.refresh(current, LockModeType.PESSIMISTIC_WRITE);
        if (!current.isActiveAccount()) throw invalidRefresh();
        // 비밀번호 확인 뒤 재설정이 먼저 완료되었으면 이전 확인 결과로 세션을 다시 만들지 않는다.
        if (authenticatedHash != null && !java.util.Objects.equals(authenticatedHash, current.getPassword())) {
            throw invalidRefresh();
        }
        user = current;
        // 로그인마다 독립된 식별자를 발급한다. 다른 브라우저·기기의 세션 행은 변경하지 않는다.
        String sessionId = UUID.randomUUID().toString();
        String refresh = jwt.generateRefreshToken(user.getId(), user.getEmail(), sessionId);
        Instant now = Instant.now();
        sessions.saveAndFlush(AuthSession.create(sessionId, user.getId(), hash(refresh), now,
                jwt.extractExpiration(refresh).toInstant()));

        // 기존 웹과 모바일이 같은 JSON 계약을 사용하고 refresh 원문은 응답에만 반환한다.
        return response(user, jwt.generateAccessToken(user.getId(), user.getEmail(), sessionId), refresh);
    }

    @Transactional
    public UserLoginResponseDto refresh(String refreshToken, Long userId, String sessionId) {
        // 갱신과 logout은 같은 행을 잠가 폐기 상태를 덮어쓰거나 되살리지 않는다.
        AuthSession session = sessions.lockByIdAndUserId(sessionId, userId).orElseThrow(this::invalidRefresh);
        if (!session.activeAt(Instant.now()) || !sameHash(session.getRefreshTokenHash(), hash(refreshToken))) {
            throw invalidRefresh();
        }
        User user = users.findById(userId).orElseThrow(this::invalidRefresh);
        if (!user.isActiveAccount()) throw invalidRefresh();

        // NextAuth의 동시 갱신과 호환되도록 기존 refresh 재사용 계약을 유지한다.
        // 새 access도 같은 sid를 가지므로 이후 logout이 완료되면 보호 경계에서 거부된다.
        return response(user, jwt.generateAccessToken(userId, user.getEmail(), sessionId), refreshToken);
    }

    @Transactional
    public void revoke(Long userId, String sessionId) {
        // 서명 검증으로 얻은 현재 사용자와 sid가 모두 일치하는 행만 폐기한다.
        sessions.lockByIdAndUserId(sessionId, userId).ifPresent(session -> session.revoke(Instant.now()));
    }

    private UserLoginResponseDto response(User user, String accessToken, String refreshToken) {
        long expiresIn = Math.max(0, (jwt.extractExpiration(accessToken).getTime() - System.currentTimeMillis()) / 1000);
        return UserLoginResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresIn(expiresIn)
                .role(user.getAuthority())
                .publicId(user.getPublicId())
                .build();
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required");
        }
    }

    private boolean sameHash(String expected, String actual) {
        return expected != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }

    private AuthException invalidRefresh() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "다시 로그인해 주세요.");
    }
}
