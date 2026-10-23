package jp.co.translacat.domain.user.deletion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AccountDeletionSettingsBindingTest {
    @ParameterizedTest
    @CsvSource({"false,false", "true,true", "false,true", "true,false"})
    void publicDeploymentFlagNamesBindIndependently(boolean enabled, boolean workerEnabled) throws IOException {
        // 준비: 실제 OS 환경·비밀 파일 없이 Docker가 받는 공개 두 이름을 그대로 사용한다.
        var environment = isolatedEnvironment(Map.of(
                "ACCOUNT_DELETION_ENABLED", Boolean.toString(enabled),
                "ACCOUNT_DELETION_WORKER_ENABLED", Boolean.toString(workerEnabled)));

        // 실행: 실제 common properties의 명시 placeholder와 Spring Binder를 함께 적용한다.
        var settings = Binder.get(environment).bind("account.deletion", AccountDeletionProperties.class).get();

        // 검증: 접수 차단/worker 유지 조합을 포함해 두 설정이 서로 바뀌거나 기본 false로 유실되지 않는다.
        assertThat(settings.isEnabled()).isEqualTo(enabled);
        assertThat(settings.isWorkerEnabled()).isEqualTo(workerEnabled);
    }

    @Test
    void absentPublicFlagsKeepInitialAcceptanceAndWorkerOff() throws IOException {
        // 준비
        var environment = isolatedEnvironment(Map.of());

        // 실행
        var settings = Binder.get(environment).bind("account.deletion", AccountDeletionProperties.class).get();

        // 검증: 환경을 읽지 않은 최초 설정은 모두 비활성이다.
        assertThat(settings.isEnabled()).isFalse();
        assertThat(settings.isWorkerEnabled()).isFalse();
    }

    private static StandardEnvironment isolatedEnvironment(Map<String, Object> values) throws IOException {
        // process 환경과 JVM -D 값도 제거한다. properties loader는 Boot의 외부 .env import를 실행하지 않는다.
        var environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, values));
        new PropertiesPropertySourceLoader().load("public-application-properties", new ClassPathResource("application.properties"))
                .forEach(source -> environment.getPropertySources().addLast(source));
        return environment;
    }
}
