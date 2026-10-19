package jp.co.translacat.domain.user.auth.mobile;

import jp.co.translacat.domain.user.auth.AuthAttemptLimiter;
import jp.co.translacat.domain.user.auth.AuthException;
import jp.co.translacat.domain.user.auth.session.AuthSessionService;
import jp.co.translacat.domain.user.dto.UserLoginResponseDto;
import jp.co.translacat.domain.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class MobileAuthService {
    private static final long REQUEST_TTL_SECONDS = 300;
    private static final long CODE_TTL_SECONDS = 60;
    private final MobileAuthRequestRepository requests;
    private final MobileAuthRedirects redirects;
    private final AuthSessionService sessions;
    private final UserRepository users;
    private final AuthAttemptLimiter attempts;
    private final Clock clock;

    @Autowired
    public MobileAuthService(MobileAuthRequestRepository requests, MobileAuthRedirects redirects,
                             AuthSessionService sessions, UserRepository users, AuthAttemptLimiter attempts) {
        this(requests, redirects, sessions, users, attempts, Clock.systemUTC());
    }

    MobileAuthService(MobileAuthRequestRepository requests, MobileAuthRedirects redirects,
                      AuthSessionService sessions, UserRepository users, AuthAttemptLimiter attempts, Clock clock) {
        this.requests = requests;
        this.redirects = redirects;
        this.sessions = sessions;
        this.users = users;
        this.attempts = attempts;
        this.clock = clock;
    }

    @Transactional
    public RequestResponse create(String codeChallenge, String state, String redirectUri, String peer) {
        // 공개 입력을 제한하고 허용된 앱 경로만 저장한다. verifier나 Google 토큰은 받지 않는다.
        requireMatch(codeChallenge, "[A-Za-z0-9_-]{43}");
        requireMatch(state, "[A-Za-z0-9_-]{43,128}");
        redirects.requireAllowed(redirectUri);
        acquire("MOBILE_CREATE_PEER", peerIdentity(peer));

        Instant now = clock.instant();
        String requestId = UUID.randomUUID().toString();
        requests.saveAndFlush(MobileAuthRequest.create(requestId, codeChallenge, state, redirectUri,
                now, now.plusSeconds(REQUEST_TTL_SECONDS)));
        return new RequestResponse(requestId, REQUEST_TTL_SECONDS);
    }

    @Transactional
    public AuthorizeResponse authorize(String requestId, Long userId) {
        // HTTP 경계는 BFF 서명을 확인하며 승인 주체는 검증된 Bearer principal만 사용한다.
        // 같은 요청의 다른 승인·교환과 행 잠금을 공유한다.
        String id = requireRequestId(requestId);
        if (userId == null || userId <= 0) throw invalidProof();
        acquire("MOBILE_APPROVE_USER", String.valueOf(userId));
        MobileAuthRequest request = lock(id);
        Instant now = clock.instant();
        requireNotExpired(request, now);
        redirects.requireAllowed(request.getRedirectUri());
        if (request.getConsumedAt() != null) throw alreadyUsed();
        if (request.getApprovedAt() != null) {
            throw new AuthException(HttpStatus.CONFLICT, "MOBILE_AUTH_ALREADY_APPROVED", "이미 승인한 로그인 요청입니다.");
        }
        if (!users.existsById(userId)) throw invalidProof();

        // 원문 코드는 이 응답 한 번에만 반환한다. 요청 전체 300초와 코드 최대 60초를 함께 지킨다.
        String code = MobileAuthProofs.issueCode();
        Instant codeExpiry = now.plusSeconds(CODE_TTL_SECONDS);
        if (codeExpiry.isAfter(request.getExpiresAt())) codeExpiry = request.getExpiresAt();
        request.approve(userId, MobileAuthProofs.hash(code), now, codeExpiry);
        return new AuthorizeResponse(request.getRedirectUri(), code, request.getState());
    }

    @Transactional
    public UserLoginResponseDto exchange(String requestId, String code, String codeVerifier, String peer) {
        // RFC 7636 unreserved verifier만 허용하고 요청별·연결 주소별 잘못된 증명 시도를 제한한다.
        String id = requireRequestId(requestId);
        requireMatch(code, "[A-Za-z0-9_-]{43}");
        requireMatch(codeVerifier, "[A-Za-z0-9._~-]{43,128}");
        acquire("MOBILE_EXCHANGE_PEER", peerIdentity(peer));
        acquire("MOBILE_EXCHANGE_REQUEST", id);
        MobileAuthRequest request = lock(id);
        Instant now = clock.instant();
        requireNotExpired(request, now);
        redirects.requireAllowed(request.getRedirectUri());
        if (request.getConsumedAt() != null) throw alreadyUsed();
        if (request.getApprovedUserId() == null || request.getCodeExpiresAt() == null) throw invalidProof();
        if (!request.getCodeExpiresAt().isAfter(now)) throw expired();
        boolean codeMatches = MobileAuthProofs.same(request.getCodeHash(), MobileAuthProofs.hash(code));
        boolean pkceMatches = MobileAuthProofs.same(request.getCodeChallenge(), MobileAuthProofs.challenge(codeVerifier));
        if (!codeMatches || !pkceMatches) throw invalidProof();

        // 일회용 소비와 독립 sid 생성은 같은 트랜잭션이다. 발급 실패 시 요청 소비도 되돌린다.
        var user = users.findById(request.getApprovedUserId()).orElseThrow(this::invalidProof);
        request.consume(now);
        return sessions.create(user);
    }

    @Scheduled(fixedDelayString = "${auth.mobile.cleanup-delay-ms:60000}",
            initialDelayString = "${auth.mobile.cleanup-initial-delay-ms:60000}")
    @Transactional
    public void cleanupExpired() {
        // 한 번에 최대 500개만 정리해 긴 삭제 트랜잭션과 원문 로그를 만들지 않는다.
        var expired = requests.findExpiredIds(clock.instant(), PageRequest.of(0, 500));
        if (!expired.isEmpty()) requests.deleteAllByIdInBatch(expired);
    }

    private MobileAuthRequest lock(String id) {
        return requests.lockById(id).orElseThrow(() ->
                new AuthException(HttpStatus.BAD_REQUEST, "MOBILE_AUTH_INVALID_REQUEST", "로그인 요청을 다시 시작해 주세요."));
    }

    private void requireNotExpired(MobileAuthRequest request, Instant now) {
        if (!request.getExpiresAt().isAfter(now)) throw expired();
    }

    private String requireRequestId(String value) {
        requireMatch(value, "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
        return UUID.fromString(value).toString();
    }

    private void requireMatch(String value, String pattern) {
        if (value == null || value.length() > 128 || !value.matches(pattern)) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "MOBILE_AUTH_INVALID_INPUT", "앱 로그인 입력값을 확인해 주세요.");
        }
    }

    private String peerIdentity(String peer) {
        // 전달 헤더를 임의 신뢰하지 않는다. servlet이 제공한 연결 주소만 제한 키의 hash로 사용한다.
        return MobileAuthProofs.hash(peer == null || peer.isBlank() || peer.length() > 128 ? "unknown-peer" : peer);
    }

    private void acquire(String purpose, String identity) {
        try {
            attempts.acquire(purpose, identity);
        } catch (AuthException failure) {
            if (failure.getStatus() != HttpStatus.TOO_MANY_REQUESTS) throw failure;
            throw new AuthException(HttpStatus.TOO_MANY_REQUESTS, "MOBILE_AUTH_RATE_LIMITED",
                    "앱 로그인 요청이 많습니다. 잠시 후 다시 시도해 주세요.", failure.getRetryAfterSeconds());
        }
    }

    private AuthException invalidProof() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "MOBILE_AUTH_INVALID_PROOF", "앱 로그인 확인에 실패했습니다. 다시 시작해 주세요.");
    }

    private AuthException expired() {
        return new AuthException(HttpStatus.GONE, "MOBILE_AUTH_EXPIRED", "앱 로그인 요청이 만료되었습니다. 다시 시작해 주세요.");
    }

    private AuthException alreadyUsed() {
        return new AuthException(HttpStatus.CONFLICT, "MOBILE_AUTH_ALREADY_USED", "이미 완료한 앱 로그인 요청입니다.");
    }

    public record RequestResponse(String requestId, long expiresIn) {
    }

    public record AuthorizeResponse(String redirectUri, String code, String state) {
    }
}
