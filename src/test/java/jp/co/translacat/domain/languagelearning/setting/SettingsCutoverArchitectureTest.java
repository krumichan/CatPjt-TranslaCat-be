package jp.co.translacat.domain.languagelearning.setting;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java/JPA의 이전 Settings 저장 경로를 다시 추가하지 못하게 하는 가벼운 소스 경계 검사다.
 */
class SettingsCutoverArchitectureTest {
    private final Path root = Path.of("src/main/java/jp/co/translacat");

    @Test
    void legacySettingsImplementationsAndMappingsAreGone() throws Exception {
        var forbidden = List.of("LanguageLearningUserSettingQueryService", "LanguageLearningAdminSettingQueryService",
                "ListeningPolicySettingQueryService", "LanguageLearningUserSettingRepository",
                "LanguageLearningAdminSettingRepository",
                "LanguageLearningUserSettingCommandService", "LanguageLearningAdminSettingCommandService",
                "LanguageLearningAdminSettingAuditRepository", "ListeningPolicySettingRepository");
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = Files.readString(path);
                for (String name : forbidden) assertFalse(text.contains(name), path + " still uses " + name);
                for (String table : List.of("language_learning_user_setting", "language_learning_admin_setting",
                        "language_learning_admin_setting_audit", "language_learning_listening_policy_setting")) {
                    assertFalse(text.contains("@Table(name = \"" + table + "\""), path.toString());
                }
            }
        }
    }

    @Test
    void snapshotModelsHaveNoJpaOrSpringDependency() throws Exception {
        for (Path folder : List.of(root.resolve("domain/languagelearning/setting/model"),
                root.resolve("domain/languagelearning/listening/setting/model"))) {
            try (var paths = Files.walk(folder)) {
                for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                    var text = Files.readString(path);
                    assertFalse(text.contains("import jakarta.persistence."));
                    assertFalse(text.contains("import org.springframework."));
                    assertFalse(text.contains("createDefault("));
                }
            }
        }
    }

    @Test
    void externalSettingsPathsAndAdminAuthorizationRemainUnchanged() throws Exception {
        var controllers = root.resolve("domain/languagelearning/setting/controller");
        assertTrue(Files.readString(controllers.resolve("LanguageLearningSettingController.java"))
                .contains("/api/v1/language-learning/settings"));
        var admin = Files.readString(controllers.resolve("AdminLanguageLearningSettingController.java"));
        assertTrue(admin.contains("/api/v1/admin/language-learning/settings"));
        assertTrue(admin.contains("@PreAuthorize(\"hasRole('ADMIN')\")"));
        assertTrue(admin.contains("settingFacade.get(userPrincipal.getId())"));
    }
}
