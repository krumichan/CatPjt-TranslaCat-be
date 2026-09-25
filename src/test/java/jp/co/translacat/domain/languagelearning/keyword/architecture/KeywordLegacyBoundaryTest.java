package jp.co.translacat.domain.languagelearning.keyword.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 残存参照の軽量検査。バイトコード解析や分散トランザクション検証ではない。
 */
class KeywordLegacyBoundaryTest {
    @Test
    void productionSourcesDoNotReferenceRetiredCatalogEntitiesOrServices() throws Exception {
        Path main = Path.of("src/main/java/jp/co/translacat");
        List<String> retired =
                List.of("SystemKeyword", "CustomKeyword", "SystemKeywordLocale", "UserSystemKeywordSelection",
                        "SystemKeywordRepository", "CustomKeywordRepository", "SystemKeywordLocaleRepository",
                        "UserSystemKeywordSelectionRepository", "CustomKeywordCommandService",
                        "SystemKeywordCommandService", "SystemKeywordSelectionCommandService",
                        "LanguageLearningKeywordQueryService", "KeywordApplicationTimingPolicy",
                        "KeywordValidationPolicy", "KeywordHierarchyPolicy");
        var forbidden = Pattern.compile("\\b(" + String.join("|", retired) + ")\\b");
        assertTrue(Files.isDirectory(main));
        try (var paths = Files.walk(main)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                assertFalse(forbidden.matcher(Files.readString(path)).find(), path.toString());
            }
        }
    }

    @Test
    void learnerMasteryIsReadFromLlWithoutRetiredCoreEntity() throws Exception {
        Path root = Path.of("src/main/java/jp/co/translacat/domain/languagelearning");
        assertFalse(Files.exists(root.resolve("keyword/entity/KeywordMastery.java")));
        String service = Files.readString(root.resolve("keyword/service/KeywordCandidateQueryService.java"));
        assertTrue(service.contains("KeywordCatalogGateway"));
        assertTrue(service.contains("GrowthReadGateway"));
        assertFalse(service.contains("KeywordMasteryRepository"));
        assertTrue(service.contains("weightPolicy.calculateRawWeight"));
        assertFalse(service.contains("new SystemKeyword"));
    }
}
