package jp.co.translacat.domain.user.pushinstallation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class PushInstallationValidationTest {
    private static final String PROJECT = "11111111-1111-4111-8111-111111111111";

    @ParameterizedTest @ValueSource(strings = {"ExpoPushToken", "ExponentPushToken"})
    void token_boundary_matches_mobile_256_character_payload(String prefix) {
        // 준비 / 실행 / 검증
        var accepted = input(prefix + "[" + "x".repeat(256) + "]", 1);
        assertThatCode(() -> PushInstallationValidation.put(accepted)).doesNotThrowAnyException();
        assertThatThrownBy(() -> PushInstallationValidation.put(input(prefix + "[" + "x".repeat(257) + "]", 1)))
                .isInstanceOf(PushInstallationException.class);
    }

    @Test void malformed_proof_token_uuid_and_unsafe_generation_are_rejected_without_reflection() {
        // 준비 / 실행 / 검증
        for (String token : List.of("native-fcm-token", "ExpoPushToken[bad+character]", "ExpoPushToken[]", "ExpoPushToken[x]\n")) {
            assertThatThrownBy(() -> PushInstallationValidation.put(input(token, 1))).isInstanceOf(PushInstallationException.class);
        }
        for (long generation : List.of(0L, -1L, 9_007_199_254_740_992L)) {
            assertThatThrownBy(() -> PushInstallationValidation.put(input("ExpoPushToken[x]", generation))).isInstanceOf(PushInstallationException.class);
        }
        assertThatThrownBy(() -> PushInstallationValidation.proof("A".repeat(64))).isInstanceOf(PushInstallationException.class);
        assertThatThrownBy(() -> PushInstallationValidation.uuid(PROJECT.toUpperCase().replace("1", "A"))).isInstanceOf(PushInstallationException.class);
    }

    @Test void explicit_configuration_is_closed_by_default_and_never_allows_over_ten_devices() {
        // 준비 / 실행 / 검증
        var options = new PushInstallationProperties();
        assertThatThrownBy(options::requireConfigured).isInstanceOfSatisfying(PushInstallationException.class,
                error -> assertThat(error.code()).isEqualTo("PUSH_NOT_CONFIGURED"));
        options.setEnabled(true); options.setAllowedProjects(Map.of("development", List.of(PROJECT)));
        assertThatCode(() -> options.requireAllowed("development", PROJECT)).doesNotThrowAnyException();
        options.setMaximumActivePerUser(11);
        assertThatThrownBy(options::requireConfigured).isInstanceOf(PushInstallationException.class);
        options.setMaximumActivePerUser(10); options.setAllowedProjects(Map.of("production", List.of("not-a-real-project")));
        assertThatThrownBy(options::requireConfigured).isInstanceOf(PushInstallationException.class);
    }

    private static PushInstallationDtos.Put input(String token, long generation) {
        return new PushInstallationDtos.Put("expo", token, "android", "development", PROJECT, "a".repeat(64), generation);
    }
}
