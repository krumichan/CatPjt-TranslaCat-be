package jp.co.translacat.global.utils;

import lombok.experimental.UtilityClass;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * SecurityUtility 클래스
 *
 * Spring Security에서 현재 인증(Authentication) 정보와 사용자 정보(UserDetails, 권한 등)를
 * 가져오기 위한 유틸리티 클래스입니다.
 */
@UtilityClass
public class SecurityUtil {

    /**
     * 현재 스레드(SecurityContext)에 있는 Authentication 객체 가져오기
     *
     * @return Authentication 객체, 인증 정보가 없으면 null
     */
    public static Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * 현재 인증된 사용자(UserDetails) 가져오기
     *
     * @return UserDetails 객체, 인증 정보 없으면 ClassCastException 가능
     */
    public static UserDetails getPrincipal() {
        return (UserDetails) SecurityUtil.getAuthentication().getPrincipal();
    }

    /**
     * 현재 인증된 사용자의 이메일(username) 가져오기
     *
     * @return 사용자 이메일(username)
     */
    public static String getUsername() {
        return SecurityUtil.getPrincipal().getUsername();
    }

    /**
     * 현재 인증된 사용자의 권한 가져오기
     *
     * @return 권한 문자열 (GrantedAuthority.getAuthority())
     *         여러 권한이 있을 경우 첫 번째 권한만 반환
     */
    public static String getAuthority() {
        Collection<? extends GrantedAuthority> authorities = SecurityUtil.getPrincipal().getAuthorities();
        return authorities.stream()
                .map(GrantedAuthority::getAuthority) // GrantedAuthority -> String
                .findFirst() // 권한은 하나만 가지고 있다고 가정
                .orElse(null);
    }

    /**
     * 현재 인증된 사용자의 이름을 안전하게 가져오기
     * <p>
     * 인증 정보가 없거나 익명 사용자(Anonymous)인 경우 "GUEST"를 반환하여
     * NullPointerException이나 ClassCastException을 방지합니다.
     * </p>
     *
     * @return 사용자 이름 또는 "GUEST"
     */
    public static String getSafeUsername() {
        Authentication auth = getAuthentication();

        // 인증 정보가 없거나, 익명 사용자(anonymousUser)인 경우 처리
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "GUEST";
        }

        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        }

        return principal.toString();
    }
}
