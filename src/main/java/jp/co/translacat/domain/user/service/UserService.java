package jp.co.translacat.domain.user.service;

import jp.co.translacat.domain.user.dto.UserCreateRequestDto;
import jp.co.translacat.domain.user.dto.UserLoginRequestDto;
import jp.co.translacat.domain.user.dto.UserLoginResponseDto;
import jp.co.translacat.domain.user.entity.RefreshToken;
import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.deletion.UserAccountWriteFence;
import jp.co.translacat.domain.user.deletion.AccountDeletionIdentityGate;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jp.co.translacat.domain.user.entity.UserAllowed;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.domain.user.enums.SocialType;
import jp.co.translacat.domain.user.repository.RefreshTokenRepository;
import jp.co.translacat.domain.user.repository.UserAllowedRepository;
import jp.co.translacat.domain.user.repository.UserRepository;
import jp.co.translacat.domain.user.auth.AuthException;
import jp.co.translacat.domain.user.auth.AuthInputs;
import jp.co.translacat.domain.user.auth.AuthAttemptLimiter;
import jp.co.translacat.domain.user.auth.session.AuthSessionService;
import jp.co.translacat.domain.user.auth.email.EmailVerificationService;
import jp.co.translacat.domain.user.auth.email.EmailVerificationPurpose;
import jp.co.translacat.domain.user.profile.entity.UserProfile;
import jp.co.translacat.domain.user.profile.repository.UserProfileRepository;
import jp.co.translacat.global.security.JWTService;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.global.utils.PublicIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;

// import org.springframework.security.access.AccessDeniedException;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserAllowedRepository userAllowedRepository;

    private final JWTService jwtService;

    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerification;
    private final UserProfileRepository userProfiles;
    private final AuthAttemptLimiter attempts;
    private final AuthSessionService sessions;
    private final AccountDeletionIdentityGate deletionIdentities;

    @PersistenceContext
    private EntityManager entities;

    public User getById(Long userId) {
        return userRepository.findById(userId)
                .map(user -> UserAccountWriteFence.lock(entities, user))
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email).map(user -> UserAccountWriteFence.lock(entities, user)).orElse(null);
    }

    public User findByPublicId(String publicId) {
        return userRepository.findByPublicId(publicId).map(user -> UserAccountWriteFence.lock(entities, user)).orElse(null);
    }

    public Optional<UserAllowed> findUserAllowedByEmail(String email) {
        return userAllowedRepository.findByEmail(email);
    }

    @Transactional
    public User register(UserCreateRequestDto request) {
        // 입력 규칙은 Bean Validation 제공자 유무와 관계없이 서비스 경계에서 적용한다.
        String email = AuthInputs.normalizeEmail(request.getEmail());
        AuthInputs.validatePassword(request.getPassword());
        String nickname = AuthInputs.validateNickname(request.getNickname());
        var birthDate = AuthInputs.validateBirthDate(request.getBirthDate());

        // 인증 증명과 계정/프로필 생성은 같은 트랜잭션에 묶어 실패 시 모두 되돌린다.
        deletionIdentities.requireNewAccountAllowed(email);
        emailVerification.consume(request.getVerificationToken(), email, EmailVerificationPurpose.REGISTER, null);
        if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new AuthException(HttpStatus.CONFLICT, "ACCOUNT_ALREADY_EXISTS", "이미 가입된 이메일입니다. 기존 방식으로 로그인해 주세요.");
        }
        User user = User.createLocalUser(email, passwordEncoder.encode(request.getPassword()),
                nickname, Role.USER, generateUniquePublicId());
        user.setBirthDate(birthDate);
        user.setEmailVerifiedAt(Instant.now());

        try {
            User saved = userRepository.saveAndFlush(user);
            userProfiles.saveAndFlush(UserProfile.createDefault(saved));
            return saved;
        } catch (DataIntegrityViolationException exception) {
            throw new AuthException(HttpStatus.CONFLICT, "REGISTRATION_CONFLICT", "가입 정보가 중복되었습니다. 다시 확인해 주세요.");
        }
    }

    @Transactional
    public User getOrRegisterSocialUser(String email, String username, String socialId, SocialType socialType) {
        // Google의 고유 subject를 먼저 조회해 제공자의 이메일 변경에도 기존 계정을 유지한다.
        var existing = userRepository.findBySocialIdAndSocialType(socialId, socialType);
        if (existing.isPresent()) {
            if (!existing.get().isActiveAccount()) {
                throw new AuthException(HttpStatus.UNAUTHORIZED, "ACCOUNT_INACTIVE", "다시 로그인해 주세요.");
            }
            return existing.get();
        }

        // 이메일이 같은 계정은 자동 연결하지 않고 기존 로그인 후 명시적인 연결을 요구한다.
        String normalizedEmail = AuthInputs.normalizeEmail(email);
        deletionIdentities.requireNewAccountAllowed(normalizedEmail);
        if (userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new AuthException(HttpStatus.CONFLICT, "ACCOUNT_LINK_REQUIRED",
                    "이미 가입된 이메일입니다. 기존 방식으로 로그인한 뒤 계정 설정에서 Google을 연결해 주세요.");
        }
        String displayName = username == null || username.isBlank() ? normalizedEmail.split("@")[0] : username.trim();
        User user = User.createSocialUser(normalizedEmail, displayName, socialType, socialId,
                Role.USER, generateUniquePublicId());
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new AuthException(HttpStatus.CONFLICT, "ACCOUNT_LINK_REQUIRED",
                    "이미 가입된 계정입니다. 기존 방식으로 로그인한 뒤 계정 설정을 확인해 주세요.");
        }
    }

    public UserLoginResponseDto authentication(UserLoginRequestDto userLoginRequestDto) {
        // 잘못된 비밀번호 요청도 별도 제한 저장소에 누적하여 반복 시도를 제한한다.
        String email = AuthInputs.normalizeEmail(userLoginRequestDto.getEmail());
        attempts.acquire("LOGIN", email);
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginEmail(email),
                        userLoginRequestDto.getPassword()
                )
        );

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User user = userPrincipal.user();

        // 잠금 후 refresh가 같은 엔티티를 갱신해도 검증 당시의 해시를 별도 값으로 보존한다.
        String authenticatedHash = user.getPassword();
        return sessions.createAfterPasswordAuthentication(user, authenticatedHash);
    }

    public String logout(String token) {
        // 새 세션은 현재 sid만 폐기한다. 구형 웹 토큰은 기존 단일 refresh 행만 정리한다.
        Long userId = jwtService.getAccessId(token);
        String sessionId = jwtService.getSessionId(token);
        if (sessionId == null) {
            refreshTokenRepository.deleteById(userId);
        } else {
            sessions.revoke(userId, sessionId);
        }
        return "SUCCESS";
    }

    public UserLoginResponseDto refreshAccessToken(String refreshToken) {
        // 빈 값과 비정상 길이를 JWT 파서에 전달하지 않는다.
        if (refreshToken == null || refreshToken.isBlank() || refreshToken.length() > 8192) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "다시 로그인해 주세요.");
        }
        Long userId = jwtService.getRefreshId(refreshToken);
        if (userId == null) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "다시 로그인해 주세요.");
        }

        // 신규 세션의 갱신은 sid별 hash 저장소에서 처리하고 구형 저장소와 혼용하지 않는다.
        String sessionId = jwtService.getSessionId(refreshToken);
        if (sessionId != null) {
            return sessions.refresh(refreshToken, userId, sessionId);
        }

        RefreshToken rt = refreshTokenRepository.findById(userId)
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "다시 로그인해 주세요."));

        if (!rt.getToken().equals(refreshToken)) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "다시 로그인해 주세요.");
        }

        if (rt.getExpireDate().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.deleteById(userId);
            throw new AuthException(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_EXPIRED", "다시 로그인해 주세요.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (!user.isActiveAccount()) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "ACCOUNT_INACTIVE", "다시 로그인해 주세요.");
        }

        String newAccessToken = jwtService.generateAccessToken(userId, user.getEmail());

        Date accessExpiry = jwtService.extractExpiration(newAccessToken);
        long accessTokenExpiresIn = (accessExpiry.getTime() - System.currentTimeMillis()) / 1000;

        return UserLoginResponseDto.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiresIn(accessTokenExpiresIn)
                .role(user.getAuthority())
                .publicId(user.getPublicId())
                .build();
    }

    public UserLoginResponseDto generateUserTokens(User user) {
        return sessions.create(user);
    }

    private String generateUniquePublicId() {
        for (int i = 0; i < 10; i++) {
            String publicId = PublicIdGenerator.generate();

            if (!userRepository.existsByPublicId(publicId)) {
                return publicId;
            }
        }

        throw new IllegalStateException("Failed to generate public id.");
    }

    private String loginEmail(String rawEmail) {
        String email = AuthInputs.normalizeEmail(rawEmail);
        // 과거 계정의 저장된 대소문자를 보존하면서 신규 입력의 이메일 정규화를 지원한다.
        return userRepository.findByEmailIgnoreCase(email).map(User::getEmail).orElse(email);
    }
}
