package jp.co.translacat.global.jpa;

import jp.co.translacat.domain.user.entity.User;
import jp.co.translacat.global.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditIdentityTest {

    private static class AuditedFixture extends BaseAuditable {
        void insert() {
            prePersist();
        }

        void update() {
            preUpdate();
        }
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(long id, String email) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getEmail()).thenReturn(email);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UserPrincipal(user), null, List.of()));
    }

    @Test
    void existingShortEmailRemainsTheAuditor() {
        authenticate(12L, "qa@example.invalid");
        AuditedFixture fixture = new AuditedFixture();
        fixture.insert();
        fixture.update();
        assertThat(fixture.getCreatedBy()).isEqualTo("qa@example.invalid");
        assertThat(fixture.getUpdatedBy()).isEqualTo("qa@example.invalid");
    }

    @Test
    void validLongEmailUsesStableUserIdWithinExistingAuditColumn() {
        authenticate(42L, "long-but-valid-user-address-with-more-than-fifty-characters@example.invalid");
        AuditedFixture fixture = new AuditedFixture();
        fixture.insert();
        fixture.update();
        assertThat(fixture.getCreatedBy()).isEqualTo("USER:42");
        assertThat(fixture.getUpdatedBy()).isEqualTo("USER:42");
    }
}
