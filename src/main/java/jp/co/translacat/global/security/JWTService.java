package jp.co.translacat.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * JWTService 클래스
 * <p>
 * JWT(JSON Web Token) 생성, 검증, 파싱 관련 서비스 클래스입니다.
 */
@Service
public class JWTService {

    private final String KEY_ID = "id";

    private final long accessTokenExpiredAt;
    private final long refreshTokenExpiredAt;
    private final String secretKey;

    /**
     * JWTService 생성자
     * HmacSHA256 알고리즘으로 SecretKey를 생성하고 Base64로 인코딩
     */
    public JWTService(@Value("${jwt.token.secret-key}") String secretKey,
                      @Value("${jwt.token.expired.access}") long accExpiredAt,
                      @Value("${jwt.token.expired.refresh}") long rfExpiredAt) {

        accessTokenExpiredAt = accExpiredAt;
        refreshTokenExpiredAt = rfExpiredAt;

        this.secretKey = secretKey;
    }

    /**
     * Access Token 생성
     *
     * @param userId 사용자 ID
     * @param email  사용자 Email
     * @return JWT Access Token (5분 만료)
     */
    public String generateAccessToken(Long userId, String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(this.KEY_ID, userId);
        claims.put("token_use", "access");
        return this.generateToken(email, claims, accessTokenExpiredAt);
    }

    /**
     * Refresh Token 생성
     *
     * @param userId 사용자 ID
     * @param email  사용자 Email
     * @return JWT Refresh Token (7일 만료)
     */
    public String generateRefreshToken(Long userId, String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(this.KEY_ID, userId);
        claims.put("token_use", "refresh");
        return this.generateToken(email, claims, refreshTokenExpiredAt);
    }

    /**
     * JWT 생성 공통 메서드
     *
     * @param subject   subject (JWT 주체)
     * @param claims    JWT payload
     * @param expiredAt 만료 시간(ms)
     * @return JWT 문자열
     */

    private String generateToken(String subject, Map<String, Object> claims, Long expiredAt) {
        return Jwts.builder()
                .claims()
                .add(claims)
                .subject(subject)
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiredAt))
                .and()
                .signWith(getKey())
                .compact();
    }

    /**
     * SecretKey 가져오기
     * <p>
     * Base64 디코딩 후 HMAC SHA256 키 생성
     */
    private SecretKey getKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * JWT에서 username(subject) 추출
     */
    public String extractUsername(String token) {
        return extractEmail(token);
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * JWT에서 특정 claim 추출
     *
     * @param token          JWT 문자열
     * @param claimsResolver Claims에서 특정 값 추출 함수
     * @param <T>            추출 타입
     * @return claim 값
     */
    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * JWT에서 모든 Claims 추출
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * JWT 검증
     *
     * @param token       JWT 문자열
     * @param userDetails UserDetails 객체
     * @return 유효하면 true
     */
    public boolean validateToken(String token, UserDetails userDetails) {
        // 보호 API에서는 access만 허용한다. 사용자 조회 결과와 토큰의 회원 ID도 일치해야 한다.
        Claims claims = extractAllClaims(token);
        if (!isAccessToken(claims) || !userDetails.getUsername().equals(claims.getSubject())) {
            return false;
        }
        return !(userDetails instanceof UserPrincipal principal)
                || principal.getId().equals(claims.get(KEY_ID, Long.class));
    }

    public Long getAccessId(String token) {
        Claims claims = extractAllClaims(token);
        if (!isAccessToken(claims)) {
            throw new JwtException("Access token required");
        }
        return claims.get(KEY_ID, Long.class);
    }

    public Long getRefreshId(String token) {
        Claims claims = extractAllClaims(token);
        String purpose = claims.get("token_use", String.class);
        // 기존 refresh는 DB에 저장된 원문과의 일치 검증을 서비스에서 계속 수행한다.
        if (purpose != null && !"refresh".equals(purpose)) {
            throw new JwtException("Refresh token required");
        }
        return claims.get(KEY_ID, Long.class);
    }

    private boolean isAccessToken(Claims claims) {
        String purpose = claims.get("token_use", String.class);
        if (purpose != null) {
            return "access".equals(purpose);
        }

        // 이전 발급기가 만든 짧은 access만 호환한다. 긴 refresh나 수명이 구분되지 않는 설정은 거부한다.
        Date issued = claims.getIssuedAt();
        Date expires = claims.getExpiration();
        if (issued == null || expires == null || refreshTokenExpiredAt <= accessTokenExpiredAt) {
            return false;
        }
        long lifetime = expires.getTime() - issued.getTime();
        return lifetime > 0 && lifetime <= accessTokenExpiredAt;
    }

    /**
     * JWT 만료 시간 추출
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * JWT payload에서 사용자 ID 추출
     */
    public Long getId(String token) {
        return this.extractAllClaims(token).get(this.KEY_ID, Long.class);
    }
}
