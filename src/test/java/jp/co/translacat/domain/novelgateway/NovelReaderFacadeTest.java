package jp.co.translacat.domain.novelgateway;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.domain.user.enums.Role;
import jp.co.translacat.global.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NovelReaderFacadeTest {
    private final NovelReaderQueryService queries = mock(NovelReaderQueryService.class);
    private final NovelReaderCommandService commands = mock(NovelReaderCommandService.class);
    private final NovelReaderFacade facade = new NovelReaderFacade(queries, commands);
    private final NovelReaderAddress address = new NovelReaderAddress("syosyetu", "n1234ab", "1");

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void onlyVerifiedAdministratorIdentityReachesService() {
        // 준비
        var user = new User();
        user.setId(12L);
        user.setAuthority(Role.ADMIN);
        var principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        // 실행 및 검증
        facade.reader(address);
        verify(queries).reader(12L, address);
        verifyNoInteractions(commands);
    }

    @Test
    void anonymousAndForgedPrincipalNeverReachInternalService() {
        // 준비 및 실행: 헤더/문자열 기반 이름만으로 내부 주체를 만들지 않는다.
        assertThatThrownBy(() -> facade.reader(address)).hasMessage("NOVEL_NOT_FOUND");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("12", null,
                        java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));

        // 검증
        assertThatThrownBy(() -> facade.reader(address)).hasMessage("NOVEL_NOT_FOUND");
        verifyNoInteractions(queries, commands);
    }

    @Test
    void unsafePathsAndInvalidPaidCommandsAreRejected() {
        // 실행 및 검증
        assertThatThrownBy(() -> new NovelReaderAddress("syosyetu", "..", "https://private"))
                .hasMessage("NOVEL_REQUEST_INVALID");
        assertThatThrownBy(() -> new NovelReaderCommandService.AudioRequest("r1", "s1", "ko", -1))
                .hasMessage("NOVEL_REQUEST_INVALID");
        assertThatThrownBy(() -> new NovelReaderCommandService.TranslationStart("r1", "", false))
                .hasMessage("NOVEL_REQUEST_INVALID");
    }

    @Test
    void cancellationRequiresTheVerifiedActor() {
        // 준비 및 실행: 익명 취소는 내부 명령까지 도달하지 않는다.
        assertThatThrownBy(() -> facade.cancel(address, "job_1")).hasMessage("NOVEL_NOT_FOUND");
        verifyNoInteractions(commands);
        var user = new User();
        user.setId(12L);
        user.setAuthority(Role.ADMIN);
        var principal = new UserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        // 실행 및 검증
        facade.cancel(address, "job_1");
        verify(commands).cancel(12L, address, "job_1");
        verifyNoInteractions(queries);
    }
}
