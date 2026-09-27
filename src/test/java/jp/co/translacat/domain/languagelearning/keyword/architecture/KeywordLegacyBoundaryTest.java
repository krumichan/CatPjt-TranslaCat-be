package jp.co.translacat.domain.languagelearning.keyword.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 제거한 Core 업무의 재참조를 검사한다. 실제 LL 동작은 별도 DB/HTTP 검사로 확인한다.
 */
class KeywordLegacyBoundaryTest {
    @Test
    void productionSourcesDoNotReferenceRetiredCatalogEntitiesOrServices() throws Exception {
        // 준비
        Path main = Path.of("src/main/java/jp/co/translacat");
        List<String> retired =
                List.of("SystemKeyword", "CustomKeyword", "SystemKeywordLocale", "UserSystemKeywordSelection",
                        "SystemKeywordRepository", "CustomKeywordRepository", "SystemKeywordLocaleRepository",
                        "UserSystemKeywordSelectionRepository", "CustomKeywordCommandService",
                        "SystemKeywordCommandService", "SystemKeywordSelectionCommandService",
                        "LanguageLearningKeywordQueryService", "KeywordApplicationTimingPolicy",
                        "KeywordValidationPolicy", "KeywordHierarchyPolicy");
        var forbidden = Pattern.compile("\\b(" + String.join("|", retired) + ")\\b");
        // 실행 및 검증
        assertTrue(Files.isDirectory(main));
        try (var paths = Files.walk(main)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                assertFalse(forbidden.matcher(Files.readString(path)).find(), path.toString());
            }
        }
    }

    @Test
    void learnerMasteryIsReadFromLlWithoutRetiredCoreEntity() throws Exception {
        // 준비: 키워드 선택 업무는 이제 LL 문맥 준비에 포함되고 BE에는 외부 조회 계약만 남는다.
        Path root = Path.of("src/main/java/jp/co/translacat/domain/languagelearning");

        // 실행 및 검증
        assertFalse(Files.exists(root.resolve("keyword/entity/KeywordMastery.java")));
        assertFalse(Files.exists(root.resolve("keyword/service/KeywordCandidateQueryService.java")));
        assertFalse(Files.exists(root.resolve("keyword/service/KeywordSelectionCommandService.java")));
        assertTrue(Files.exists(root.resolve("keyword/port/KeywordCatalogGateway.java")));
        assertTrue(Files.exists(root.resolve("growth/port/GrowthReadGateway.java")));
    }
}
