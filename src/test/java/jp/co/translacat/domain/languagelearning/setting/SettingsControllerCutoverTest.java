package jp.co.translacat.domain.languagelearning.setting;

import jp.co.translacat.domain.languagelearning.setting.controller.AdminLanguageLearningSettingController;
import jp.co.translacat.domain.languagelearning.setting.controller.LanguageLearningSettingController;
import jp.co.translacat.domain.languagelearning.setting.facade.AdminLanguageLearningSettingFacade;
import jp.co.translacat.domain.languagelearning.setting.facade.LanguageLearningSettingFacade;
import jp.co.translacat.domain.languagelearning.setting.port.AdminSettingsGateway;
import jp.co.translacat.domain.languagelearning.setting.port.UserSettingsGateway;
import jp.co.translacat.global.security.UserPrincipal;
import jp.co.translacat.support.SettingsSnapshotFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SettingsControllerCutoverTest {
    @Configuration @EnableMethodSecurity static class ConfigurationForTest {
        @Bean AdminSettingsGateway gateway() { return mock(AdminSettingsGateway.class); }
        @Bean AdminLanguageLearningSettingFacade facade(AdminSettingsGateway gateway) { return new AdminLanguageLearningSettingFacade(gateway); }
        @Bean AdminLanguageLearningSettingController controller(AdminLanguageLearningSettingFacade facade) { return new AdminLanguageLearningSettingController(facade); }
    }
    private UserPrincipal principal() {
        var principal = mock(UserPrincipal.class); when(principal.getId()).thenReturn(987L); return principal;
    }
    @Test void userGetPreservesTheExternalEnvelopeAndAuthenticatedId() {
        var gateway = mock(UserSettingsGateway.class); var principal = principal();
        when(gateway.get(987L)).thenReturn(SettingsSnapshotFixtures.userDto());
        var response = new LanguageLearningSettingController(new LanguageLearningSettingFacade(gateway)).get(principal);
        assertEquals(200, response.getResultCode()); assertEquals("OK", response.getMessage());
        assertSame(SettingsSnapshotFixtures.userDto().getClass(), response.getBody().getClass());
        verify(gateway).get(987L);
    }
    @Test void nonAdminCannotInvokeAdminReadEvenThroughTheSpringProxy() {
        try (var context = new AnnotationConfigApplicationContext(ConfigurationForTest.class)) {
            var principal = principal();
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, "", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
            assertThrows(AccessDeniedException.class, () -> context.getBean(AdminLanguageLearningSettingController.class).get(principal));
            verifyNoInteractions(context.getBean(AdminSettingsGateway.class));
        } finally { SecurityContextHolder.clearContext(); }
    }
    @Test void adminReadUsesTheRealAuthenticatedIdAndOneEnvelope() {
        try (var context = new AnnotationConfigApplicationContext(ConfigurationForTest.class)) {
            var principal = principal(); var gateway = context.getBean(AdminSettingsGateway.class);
            when(gateway.getSettings(987L)).thenReturn(SettingsSnapshotFixtures.admin().settings());
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
            var response = context.getBean(AdminLanguageLearningSettingController.class).get(principal);
            assertEquals(200, response.getResultCode()); assertEquals(5, response.getBody().defaultDailySentenceCount());
            verify(gateway).getSettings(987L);
        } finally { SecurityContextHolder.clearContext(); }
    }
}
