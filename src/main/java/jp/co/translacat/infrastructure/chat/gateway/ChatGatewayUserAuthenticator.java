package jp.co.translacat.infrastructure.chat.gateway;

import jp.co.translacat.global.security.JWTService;
import jp.co.translacat.global.security.MyUserDetailsService;
import jp.co.translacat.global.security.UserPrincipal;

public final class ChatGatewayUserAuthenticator {
    private final JWTService jwt;
    private final MyUserDetailsService users;

    public ChatGatewayUserAuthenticator(JWTService jwt, MyUserDetailsService users) {
        this.jwt = jwt;
        this.users = users;
    }

    public UserPrincipal authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() > 8200) return null;

        try {
            // 기존 서명·email 조회를 사용하되 현재 DB ID까지 대조한다. 공개 JWT 형식/발급은 변경하지 않는다.
            String token = authorization.substring(7);
            String email = jwt.extractUsername(token);
            if (email == null || email.isBlank()) return null;
            var current = users.loadUserByUsername(email);
            if (!(current instanceof UserPrincipal principal) || !jwt.validateToken(token, principal)
                    || principal.getId() == null || principal.getId() <= 0
                    || !principal.getId().equals(jwt.getId(token)) || !principal.isEnabled()
                    || !principal.isAccountNonExpired() || !principal.isAccountNonLocked()
                    || !principal.isCredentialsNonExpired()) return null;
            return principal;
        } catch (Exception ignored) {
            // 원본 토큰, 계정 식별자, 라이브러리 예외를 외부로 노출하지 않는다.
            return null;
        }
    }
}
