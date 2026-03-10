package jp.co.translacat.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * JWTService 클래스
 *
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
     * @param userId   사용자 ID
     * @param username 사용자 이름
     * @return JWT Access Token (5분 만료)
     */
    public String generateAccessToken(Long userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(this.KEY_ID, userId);
        return this.generateToken(username, claims, accessTokenExpiredAt);
    }

    /**
     * Refresh Token 생성
     *
     * @param userId   사용자 ID
     * @param username 사용자 이름
     * @return JWT Refresh Token (7일 만료)
     */
    public String generateRefreshToken(Long userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(this.KEY_ID, userId);
        return this.generateToken(username, claims, refreshTokenExpiredAt);
     }

    /**
     * JWT 생성 공통 메서드
     *
     * @param username  subject (JWT 주체)
     * @param claims    JWT payload
     * @param expiredAt 만료 시간(ms)
     * @return JWT 문자열
     */
    private String generateToken(String username, Map<String, Object> claims, Long expiredAt) {
        return Jwts.builder()
                .claims()
                .add(claims)
                .subject(username)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiredAt))
                .and()
                .signWith(getKey())
                .compact(); // 압축
    }

    /**
     * SecretKey 가져오기
     *
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
        // extract the username from jwt token
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
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    /**
     * JWT 만료 여부 확인
     */
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
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
