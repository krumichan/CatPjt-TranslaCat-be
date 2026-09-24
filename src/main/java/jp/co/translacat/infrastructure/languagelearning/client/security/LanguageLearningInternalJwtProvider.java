package jp.co.translacat.infrastructure.languagelearning.client.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jp.co.translacat.infrastructure.languagelearning.client.config.LanguageLearningClientProperties;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;

public class LanguageLearningInternalJwtProvider {

    private static final long MIN_TTL_SECONDS = 15;
    private static final long MAX_TTL_SECONDS = 300;

    private final String issuer;
    private final String audience;
    private final String callerService;
    private final long ttlSeconds;
    private final SecretKey key;
    private final Clock clock;

    public LanguageLearningInternalJwtProvider(
            LanguageLearningClientProperties properties,
            Clock clock
    ) {
        LanguageLearningClientProperties.InternalJwt jwt =
                properties.getInternalJwt();

        this.issuer = requireText(jwt.getIssuer(), "issuer");
        this.audience = requireText(jwt.getAudience(), "audience");
        this.callerService = requireText(
                jwt.getCallerService(),
                "caller-service"
        );
        this.ttlSeconds = jwt.getTtlSeconds();
        this.clock = clock;

        if (ttlSeconds < MIN_TTL_SECONDS
                || ttlSeconds > MAX_TTL_SECONDS) {
            throw new IllegalArgumentException(
                    "내부 JWT TTL은 15~300초 범위여야 합니다."
            );
        }

        byte[] decoded = decodeKey(jwt.getSecretBase64());
        if (decoded.length < 32 || decoded.length > 128) {
            throw new IllegalArgumentException(
                    "내부 JWT 키는 Base64 디코딩 후 32~128바이트여야 합니다."
            );
        }
        this.key = Keys.hmacShaKeyFor(decoded);
    }

    /**
     * 사용자 권한이 없는 BE 작업자의 Settings 조회 전용 토큰이다.
     */
    public String issueSettingsServiceToken() {
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(issuer)
                .audience().add(audience).and()
                .subject(callerService)
                .claim("service", callerService)
                .claim("tokenUse", "ll-settings-service")
                .claim("scopes", List.of("settings:read"))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Core의 학습 시작 사실은 서버가 조회하며 FE 입력을 그대로 서명하지 않는다.
     */
    public String issueKeywordUserToken(Long userId, boolean hasStartedLearning) {
        return issueKeywordToken(userId, false, hasStartedLearning);
    }

    public String issueKeywordAdminToken(Long adminUserId) {
        return issueKeywordToken(adminUserId, true, false);
    }

    private String issueKeywordToken(Long userId, boolean admin, boolean started) {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("내부 JWT userId는 양수여야 합니다.");
        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(issuer)
                .audience().add(audience).and()
                .subject(userId.toString())
                .claim("service", callerService)
                .claim("tokenUse", "ll-keywords")
                .claim("roles", List.of(admin ? "ADMIN" : "USER"))
                .claim("keywordLearningStarted", started)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** 결과 수신 원장 전용 scope다. 사용자·관리자·Settings 조회 권한을 포함하지 않는다. */
    public String issueLearningResultsToken() {
        Instant now = clock.instant();
        return Jwts.builder().issuer(issuer).audience().add(audience).and().subject(callerService)
                .claim("service", callerService).claim("tokenUse", "ll-learning-results-v1")
                .claim("scopes", List.of("learning-results:write"))
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key, Jwts.SIG.HS256).compact();
    }

    public String issueUserToken(Long userId) {
        return issue(userId, false);
    }

    public String issueAdminToken(Long userId) {
        return issue(userId, true);
    }

    private String issue(Long userId, boolean administrator) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException(
                    "내부 JWT userId는 양수여야 합니다."
            );
        }

        Instant now = clock.instant();
        return Jwts.builder()
                .issuer(issuer)
                .audience()
                .add(audience)
                .and()
                .subject(userId.toString())
                .claim("service", callerService)
                .claim("tokenUse", "ll-internal")
                .claim(
                        "roles",
                        List.of(administrator ? "ADMIN" : "USER")
                )
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    private byte[] decodeKey(String secretBase64) {
        if (secretBase64 == null || secretBase64.isBlank()) {
            throw new IllegalArgumentException(
                    "내부 JWT Base64 비밀키 설정이 필요합니다."
            );
        }
        try {
            return Decoders.BASE64.decode(secretBase64);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "내부 JWT 비밀키는 유효한 Base64여야 합니다.",
                    e
            );
        }
    }

    private String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "내부 JWT " + name + " 설정이 필요합니다."
            );
        }
        return value;
    }
}
